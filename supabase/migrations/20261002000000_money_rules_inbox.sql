-- 4Money: payee → category rules and the bank notification inbox (sub-projects C + D).
-- Per-user tables like accounts/categories/transfers: user_id defaults to auth.uid(), RLS on.
-- No foreign keys to categories/accounts: transfers have none either, and offline-first
-- uploads must not fail on ordering.

create table money.payee_rules
(
    id             uuid primary key,
    user_id        uuid    not null default auth.uid() references auth.users (id) on delete cascade,
    -- Normalized payee (lower case, collapsed whitespace, no trailing terminal IDs).
    payee_pattern  text    not null check (payee_pattern <> ''),
    match_type     text    not null default 'exact' check (match_type in ('exact', 'contains')),
    category_id    uuid    not null,
    subcategory_id uuid,
    -- Optional account the rule was learned with; a card mapping takes precedence.
    account_id     uuid,
    hits           integer not null default 0,
    -- Local wall-clock time, no time zone, like transfers.time.
    last_used_at   timestamp
);
create index payee_rules_user_idx on money.payee_rules (user_id);

create table money.inbox_items
(
    id             uuid primary key,
    user_id        uuid      not null default auth.uid() references auth.users (id) on delete cascade,
    -- Notification post time as local wall-clock time.
    received_at    timestamp not null,
    source_package text      not null,
    raw_text       text      not null,
    -- Decimal amount as parsed (not minor units: the currency may be unknown to the app).
    amount         numeric,
    currency_code  text,
    payee          text,
    card_last4     text,
    account_id     uuid,
    status         text      not null default 'pending' check (status in ('pending', 'done', 'dismissed')),
    transfer_id    uuid,
    dedup_hash     text      not null
);
create index inbox_items_user_status_idx on money.inbox_items (user_id, status, received_at desc);
create index inbox_items_user_dedup_idx on money.inbox_items (user_id, dedup_hash);

alter table money.payee_rules enable row level security;
alter table money.inbox_items enable row level security;

create policy payee_rules_own on money.payee_rules
    for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy inbox_items_own on money.inbox_items
    for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

grant select, insert, update, delete on money.payee_rules, money.inbox_items to authenticated;
grant all on money.payee_rules, money.inbox_items to service_role;
grant select on money.payee_rules, money.inbox_items to powersync_role;

alter publication powersync add table money.payee_rules, money.inbox_items;

-- Same body as in 20261001000000_money_schema.sql, only the writable table list is extended.
-- CREATE OR REPLACE keeps the existing grants (execute to authenticated only).
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
end;
$$;
