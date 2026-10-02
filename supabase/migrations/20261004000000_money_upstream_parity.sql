-- 4Money: behavioural parity with the upstream backend, on top of the reconstructed `money` schema.
--
-- 1. Dead letter: like upstream, RPC failures that the client would discard anyway
--    (data errors, constraint violations, bad SQL/privileges, our own validation) are recorded
--    in money.sync_errors, so the app shows its "upload errors" notice instead of losing the
--    change silently. Transient errors (deadlocks, timeouts, connection issues) are still raised,
--    so the client retries them.
-- 2. Account position healing: when non-archived accounts of the same user and type end up with
--    equal or invalid positions (e.g. two devices adding/moving accounts offline), positions in
--    that group are renumbered 1..n at commit, keeping the visible order.
-- 3. Transfers: concurrency-safe idempotency and deterministic account locking.
-- 4. Indexes on transfer counterparties and the category parent FK.
--
-- Safe to apply once on a live DB with the previous migrations: no data is removed or rewritten,
-- except renumbering positions of account groups that are already broken (see the end of file).

set search_path = money;

-- Dead letter --------------------------------------------------------------------------------

-- Records a failed upload for the calling user. Security definer, as sync_errors is read-only
-- for clients; the user is always taken from the JWT, never from the arguments.
create or replace function money._log_sync_error(sql_state text, error_message text, payload jsonb)
    returns boolean
    language plpgsql
    security definer
    set search_path = money
as
$$
declare
    current_user_id uuid := auth.uid();
begin
    if current_user_id is null then
        return false;
    end if;

    insert into money.sync_errors (id, user_id, details)
    values ((extract(epoch from clock_timestamp()) * 1000)::bigint::text
                || '-' || left(gen_random_uuid()::text, 8),
            current_user_id,
            jsonb_build_object(
                    'time', clock_timestamp(),
                    'sqlstate', sql_state,
                    'message', error_message,
                    'payload', payload
            ));

    return true;
end;
$$;

-- Whether an error must go to the dead letter instead of being retried by the client.
-- Matches the client's own "fatal" classes (22, 23, 42501) plus the rest of class 42
-- (e.g. an unknown column from a newer client) and P0001 (our validation exceptions).
create or replace function money._is_dead_letter_error(sql_state text) returns boolean
    language sql
    immutable
    set search_path = money
as
$$
select left(sql_state, 2) in ('22', '23', '42') or sql_state = 'P0001';
$$;

revoke all on function money._log_sync_error(text, text, jsonb) from public, anon;
revoke all on function money._is_dead_letter_error(text) from public, anon;
grant execute on function money._log_sync_error(text, text, jsonb) to authenticated;
grant execute on function money._is_dead_letter_error(text) to authenticated;

-- atomic_crud --------------------------------------------------------------------------------

-- Same contract as before (the writable table whitelist is kept), the work is now done in an
-- inner block: on a dead-letter error the whole batch is rolled back, recorded and acknowledged.
create or replace function money.atomic_crud(operations jsonb) returns void
    language plpgsql
    set search_path = money
as
$$
declare
    operation      jsonb;
    table_name     text;
    row_id         text;
    op             text;
    op_data        jsonb;
    column_data    jsonb;
    column_name    text;
    column_value   text;
    insert_columns text;
    insert_values  text;
    upsert_sets    text;
    update_sets    text;
    comma          text;
    err_state      text;
    err_message    text;
begin
    begin
        for operation in select jsonb_array_elements(operations)
            loop
                table_name := operation ->> 't';
                row_id := operation ->> 'id';
                op := operation ->> 'o';
                op_data := operation -> 'd';

                if table_name is null or row_id is null or op is null then
                    raise exception 'Invalid operation: t (table), id, and o (operation) are required fields';
                end if;

                if table_name not in ('accounts', 'categories', 'transfers', 'payee_rules', 'inbox_items') then
                    raise exception insufficient_privilege using message = 'Table ' || table_name || ' is not writable';
                end if;

                if op not in ('I', 'U', 'D') then
                    raise exception 'Invalid operation type: %. Must be I (upsert), U (update), or D (delete)', op;
                end if;

                if op in ('I', 'U') and op_data is null then
                    raise exception 'd (data) is required for I (upsert) and U (update) operations';
                end if;

                if op = 'D' then
                    execute format('delete from money.%I where id = %L', table_name, row_id);
                    continue;
                end if;

                insert_columns := '';
                insert_values := '';
                upsert_sets := '';
                update_sets := '';
                comma := '';

                if op = 'I' then
                    insert_columns := 'id';
                    insert_values := quote_literal(row_id);
                    upsert_sets := 'id = excluded.id';
                    comma := ',';
                end if;

                for column_data in select jsonb_array_elements(op_data)
                    loop
                        column_name := quote_ident(column_data ->> 0);
                        column_value := case
                                            when jsonb_typeof(column_data -> 1) = 'null' then 'NULL'
                                            when jsonb_typeof(column_data -> 1) = 'string'
                                                then quote_literal(column_data ->> 1)
                                            else column_data ->> 1
                            end;

                        if column_name is null then
                            raise exception 'Column name cannot be null';
                        end if;

                        if op = 'I' then
                            insert_columns := insert_columns || comma || column_name;
                            insert_values := insert_values || comma || column_value;
                            upsert_sets := upsert_sets || comma || column_name || ' = excluded.' || column_name;
                        else
                            update_sets := update_sets || comma || column_name || ' = ' || column_value;
                        end if;

                        comma := ',';
                    end loop;

                if op = 'I' then
                    execute format(
                            'insert into money.%I (%s) values (%s) on conflict (id) do update set %s',
                            table_name, insert_columns, insert_values, upsert_sets
                            );
                elsif update_sets <> '' then
                    execute format(
                            'update money.%I set %s where id = %L',
                            table_name, update_sets, row_id
                            );
                end if;
            end loop;
    exception
        when others then
            get stacked diagnostics err_state = returned_sqlstate, err_message = message_text;
            if money._is_dead_letter_error(err_state)
                and money._log_sync_error(err_state, err_message,
                                          jsonb_build_object('atomic_crud', operations)) then
                raise warning 'atomic_crud error recorded: %, %', err_state, err_message;
                return;
            end if;
            raise;
    end;
end;
$$;

-- Transfers ------------------------------------------------------------------------------------

-- Locks the account rows among the given counterparties in a stable order, so concurrent
-- transfers between the same accounts in opposite directions can't deadlock.
create or replace function money._lock_accounts(counterparty_ids uuid[]) returns void
    language sql
    set search_path = money
as
$$
select null
from (select a.id
      from money.accounts a
      where a.id = any (counterparty_ids)
      order by a.id
          for update) locked;
$$;

revoke all on function money._lock_accounts(uuid[]) from public, anon;
grant execute on function money._lock_accounts(uuid[]) to authenticated;

-- Create a transfer and apply it to account balances. Idempotent by ID, also under concurrency:
-- the balance effect is applied only by the call that actually inserted the row.
create or replace function money.transfer(
    id uuid,
    source_id uuid,
    source_amount numeric,
    destination_id uuid,
    destination_amount numeric,
    memo text,
    "time" text
) returns void
    language plpgsql
    set search_path = money
as
$$
declare
    inserted_id uuid;
    err_state   text;
    err_message text;
begin
    begin
        insert into money.transfers as t (id, time, source_id, source_amount, destination_id,
                                          destination_amount, memo)
        values (transfer.id, transfer."time"::timestamp, transfer.source_id, transfer.source_amount,
                transfer.destination_id, transfer.destination_amount, transfer.memo)
        on conflict on constraint transfers_pkey do nothing
        returning t.id into inserted_id;

        if inserted_id is null then
            -- Already processed: a retried upload.
            return;
        end if;

        perform money._lock_accounts(array [transfer.source_id, transfer.destination_id]);
        perform money._apply_balance_delta(transfer.source_id, -transfer.source_amount);
        perform money._apply_balance_delta(transfer.destination_id, transfer.destination_amount);
    exception
        when others then
            get stacked diagnostics err_state = returned_sqlstate, err_message = message_text;
            if money._is_dead_letter_error(err_state)
                and money._log_sync_error(err_state, err_message,
                                          jsonb_build_object('transfer', jsonb_build_object(
                                                  'id', transfer.id,
                                                  'source_id', transfer.source_id,
                                                  'source_amount', transfer.source_amount,
                                                  'destination_id', transfer.destination_id,
                                                  'destination_amount', transfer.destination_amount,
                                                  'memo', transfer.memo,
                                                  'time', transfer."time"))) then
                raise warning 'transfer error recorded: %, %', err_state, err_message;
                return;
            end if;
            raise;
    end;
end;
$$;

-- Edit a transfer: revert the old balance effect and apply the new one. Handles changed
-- counterparties and amounts (including cross-currency ones) the same way, as the effect is
-- always "minus source_amount on source, plus destination_amount on destination".
-- Falls back to creation if the transfer doesn't exist yet.
create or replace function money.transfer_edit(
    id uuid,
    source_id uuid,
    source_amount numeric,
    destination_id uuid,
    destination_amount numeric,
    memo text,
    "time" text
) returns void
    language plpgsql
    set search_path = money
as
$$
declare
    old         money.transfers;
    err_state   text;
    err_message text;
begin
    begin
        select * into old from money.transfers t where t.id = transfer_edit.id for update;

        if not found then
            perform money.transfer(transfer_edit.id, transfer_edit.source_id, transfer_edit.source_amount,
                                   transfer_edit.destination_id, transfer_edit.destination_amount,
                                   transfer_edit.memo, transfer_edit."time");
            return;
        end if;

        update money.transfers t
        set time               = transfer_edit."time"::timestamp,
            source_id          = transfer_edit.source_id,
            source_amount      = transfer_edit.source_amount,
            destination_id     = transfer_edit.destination_id,
            destination_amount = transfer_edit.destination_amount,
            memo               = transfer_edit.memo
        where t.id = transfer_edit.id;

        -- Skip balance writes when only time/memo changed: no needless account replication.
        if old.source_id is distinct from transfer_edit.source_id
            or old.source_amount is distinct from transfer_edit.source_amount
            or old.destination_id is distinct from transfer_edit.destination_id
            or old.destination_amount is distinct from transfer_edit.destination_amount then
            perform money._lock_accounts(array [old.source_id, old.destination_id,
                transfer_edit.source_id, transfer_edit.destination_id]);
            perform money._apply_balance_delta(old.source_id, old.source_amount);
            perform money._apply_balance_delta(old.destination_id, -old.destination_amount);
            perform money._apply_balance_delta(transfer_edit.source_id, -transfer_edit.source_amount);
            perform money._apply_balance_delta(transfer_edit.destination_id, transfer_edit.destination_amount);
        end if;
    exception
        when others then
            get stacked diagnostics err_state = returned_sqlstate, err_message = message_text;
            if money._is_dead_letter_error(err_state)
                and money._log_sync_error(err_state, err_message,
                                          jsonb_build_object('transfer_edit', jsonb_build_object(
                                                  'id', transfer_edit.id,
                                                  'source_id', transfer_edit.source_id,
                                                  'source_amount', transfer_edit.source_amount,
                                                  'destination_id', transfer_edit.destination_id,
                                                  'destination_amount', transfer_edit.destination_amount,
                                                  'memo', transfer_edit.memo,
                                                  'time', transfer_edit."time"))) then
                raise warning 'transfer_edit error recorded: %, %', err_state, err_message;
                return;
            end if;
            raise;
    end;
end;
$$;

-- Delete a transfer, reverting its balance effect. Idempotent.
create or replace function money.transfer_revert(id uuid) returns void
    language plpgsql
    set search_path = money
as
$$
declare
    old         money.transfers;
    err_state   text;
    err_message text;
begin
    begin
        delete from money.transfers t where t.id = transfer_revert.id returning * into old;

        if not found then
            return;
        end if;

        perform money._lock_accounts(array [old.source_id, old.destination_id]);
        perform money._apply_balance_delta(old.source_id, old.source_amount);
        perform money._apply_balance_delta(old.destination_id, -old.destination_amount);
    exception
        when others then
            get stacked diagnostics err_state = returned_sqlstate, err_message = message_text;
            if money._is_dead_letter_error(err_state)
                and money._log_sync_error(err_state, err_message,
                                          jsonb_build_object('transfer_revert',
                                                             jsonb_build_object('id', transfer_revert.id))) then
                raise warning 'transfer_revert error recorded: %, %', err_state, err_message;
                return;
            end if;
            raise;
    end;
end;
$$;

-- Account position healing -----------------------------------------------------------------------

-- Renumbers positions of the user's non-archived accounts of the given type to 1..n.
-- The client shows accounts by descending position, then by title, so ordering by ascending
-- position and descending title keeps what the user sees.
create or replace function money._normalize_account_positions(owner_id uuid, account_type text)
    returns void
    language plpgsql
    set search_path = money
as
$$
begin
    -- Serialize healing of the same group across concurrent transactions.
    perform pg_advisory_xact_lock(hashtextextended('money.accounts.position:' || owner_id || ':' || account_type, 0));

    update money.accounts a
    set position = ordered.new_position
    from (select g.id, row_number() over (order by g.position, g.title desc, g.id) as new_position
          from money.accounts g
          where g.user_id = owner_id
            and g.type = account_type
            and not g.is_archived) ordered
    where a.id = ordered.id
      and a.position is distinct from ordered.new_position;
end;
$$;

-- Whether the group of a given account has duplicate or invalid positions.
-- Valid Stern-Brocot positions are finite and greater than 0 (0 is the "bottom" bound).
create or replace function money._account_positions_broken(owner_id uuid, account_type text)
    returns boolean
    language sql
    stable
    set search_path = money
as
$$
select exists(select 1
              from money.accounts a
              where a.user_id = owner_id
                and a.type = account_type
                and not a.is_archived
                and (a.position <= 0 or a.position >= 'Infinity'::numeric))
           or exists(select 1
                     from money.accounts a
                     where a.user_id = owner_id
                       and a.type = account_type
                       and not a.is_archived
                     group by a.position
                     having count(*) > 1);
$$;

-- Deferred to commit: the client moves/swaps accounts as several updates in one transaction,
-- so intermediate duplicates are expected. Reads the current row state, as NEW may be stale.
create or replace function money._heal_account_positions() returns trigger
    language plpgsql
    set search_path = money
as
$$
declare
    cur money.accounts;
begin
    select * into cur from money.accounts a where a.id = new.id;

    if not found or cur.is_archived then
        return null;
    end if;

    if money._account_positions_broken(cur.user_id, cur.type) then
        perform money._normalize_account_positions(cur.user_id, cur.type);
    end if;

    return null;
end;
$$;

revoke all on function money._normalize_account_positions(uuid, text) from public, anon;
revoke all on function money._account_positions_broken(uuid, text) from public, anon;
revoke all on function money._heal_account_positions() from public, anon;
grant execute on function money._normalize_account_positions(uuid, text) to authenticated;
grant execute on function money._account_positions_broken(uuid, text) to authenticated;
grant execute on function money._heal_account_positions() to authenticated;

drop trigger if exists accounts_heal_positions on money.accounts;
create constraint trigger accounts_heal_positions
    after insert or update of position, type, is_archived
    on money.accounts
    deferrable initially deferred
    for each row
execute function money._heal_account_positions();

-- Indexes ---------------------------------------------------------------------------------------

create index if not exists transfers_source_idx on money.transfers (source_id);
create index if not exists transfers_destination_idx on money.transfers (destination_id);
create index if not exists categories_parent_idx on money.categories (parent_category_id);

-- One-off healing of existing data -------------------------------------------------------------

-- Only groups that are already broken are renumbered; healthy groups keep their exact
-- Stern-Brocot values, so nothing is rewritten (or re-synced to devices) needlessly.
do
$$
    declare
        grp record;
    begin
        for grp in select distinct a.user_id, a.type
                   from money.accounts a
                   where not a.is_archived
            loop
                if money._account_positions_broken(grp.user_id, grp.type) then
                    raise notice 'Healing account positions: user=%, type=%', grp.user_id, grp.type;
                    perform money._normalize_account_positions(grp.user_id, grp.type);
                end if;
            end loop;
    end
$$;
