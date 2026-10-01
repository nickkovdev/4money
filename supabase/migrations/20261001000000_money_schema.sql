-- 4Money server schema, reconstructed from the client contract
-- (powersync/DbSchema.kt, AtomicCrudSupabaseConnector.kt, OneMoneyConvert.kt).
-- Everything lives in the dedicated `money` schema of a shared Supabase instance.
-- The client must use `money` as the PostgREST default schema.

create schema if not exists money;

grant usage on schema money to anon, authenticated, service_role;

-- Currencies are global and read-only for users.
create table money.currencies
(
    id        uuid primary key default gen_random_uuid(),
    code      text    not null unique,
    symbol    text    not null,
    precision integer not null check (precision between 0 and 18)
);

create table money.accounts
(
    id           uuid primary key,
    user_id      uuid    not null default auth.uid() references auth.users (id) on delete cascade,
    title        text    not null,
    -- Minor units, may be very large for high precision currencies.
    balance      numeric not null default 0,
    currency_id  uuid    not null references money.currencies (id),
    -- Stern-Brocot position, numeric to round-trip the double text exactly.
    position     numeric not null default 0,
    color_scheme text    not null default 'Blue1',
    type         text    not null default 'regular' check (type in ('regular', 'savings')),
    is_archived  boolean not null default false,
    icon         text
);
create index accounts_user_idx on money.accounts (user_id);

create table money.categories
(
    id                 uuid primary key,
    user_id            uuid    not null default auth.uid() references auth.users (id) on delete cascade,
    title              text    not null,
    currency_id        uuid    not null references money.currencies (id),
    parent_category_id uuid references money.categories (id),
    is_income          boolean not null default false,
    color_scheme       text    not null default 'Blue1',
    is_archived        boolean not null default false,
    position           numeric not null default 0,
    icon               text
);
create index categories_user_idx on money.categories (user_id);

create table money.transfers
(
    id                 uuid primary key,
    user_id            uuid      not null default auth.uid() references auth.users (id) on delete cascade,
    -- Local wall-clock time, no time zone.
    time               timestamp not null,
    -- Account or category (subcategory) ID.
    source_id          uuid      not null,
    source_amount      numeric   not null,
    destination_id     uuid      not null,
    destination_amount numeric   not null,
    memo               text
);
create index transfers_user_time_idx on money.transfers (user_id, time desc);

-- Rows here make the client show "upload errors" notice.
create table money.sync_errors
(
    id      text primary key default (extract(epoch from now()) * 1000)::bigint::text,
    user_id uuid  not null references auth.users (id) on delete cascade,
    details jsonb
);

-- USD price of 1 unit of the base currency, global.
create table money.daily_prices
(
    base_currency_code text    not null,
    day                date    not null,
    price              numeric not null,
    primary key (base_currency_code, day)
);

-- RLS

alter table money.currencies enable row level security;
alter table money.accounts enable row level security;
alter table money.categories enable row level security;
alter table money.transfers enable row level security;
alter table money.sync_errors enable row level security;
alter table money.daily_prices enable row level security;

create policy currencies_read on money.currencies
    for select to authenticated using (true);
create policy daily_prices_read on money.daily_prices
    for select to authenticated using (true);

create policy accounts_own on money.accounts
    for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy categories_own on money.categories
    for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy transfers_own on money.transfers
    for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy sync_errors_own on money.sync_errors
    for select to authenticated
    using (user_id = auth.uid());

grant select on money.currencies, money.daily_prices, money.sync_errors to authenticated;
grant select, insert, update, delete on money.accounts, money.categories, money.transfers to authenticated;
grant all on all tables in schema money to service_role;

-- RPC: generic atomic CRUD,
-- https://gist.github.com/Radiokot/fbc8d1a7cf283d1f476938ca573ced82
-- adapted to the `money` schema. Runs as invoker, so RLS applies.
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

            if table_name not in ('accounts', 'categories', 'transfers') then
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

-- Applies a balance delta if the counterparty is an account; categories have no balance.
create or replace function money._apply_balance_delta(counterparty_id uuid, delta numeric) returns void
    language sql
    set search_path = money
as
$$
update money.accounts
set balance = balance + delta
where id = counterparty_id;
$$;

-- RPC: create a transfer and update account balances at the moment of sync.
-- Idempotent by ID, as the client retries uploads.
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
begin
    if exists(select 1 from money.transfers t where t.id = transfer.id) then
        return;
    end if;

    insert into money.transfers (id, time, source_id, source_amount, destination_id, destination_amount, memo)
    values (transfer.id, transfer."time"::timestamp, transfer.source_id, transfer.source_amount,
            transfer.destination_id, transfer.destination_amount, transfer.memo);

    perform money._apply_balance_delta(transfer.source_id, -transfer.source_amount);
    perform money._apply_balance_delta(transfer.destination_id, transfer.destination_amount);
end;
$$;

-- RPC: edit a transfer, reverting the old balance effect and applying the new one.
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
    old money.transfers;
begin
    select * into old from money.transfers t where t.id = transfer_edit.id for update;

    if not found then
        perform money.transfer(transfer_edit.id, transfer_edit.source_id, transfer_edit.source_amount,
                               transfer_edit.destination_id, transfer_edit.destination_amount,
                               transfer_edit.memo, transfer_edit."time");
        return;
    end if;

    perform money._apply_balance_delta(old.source_id, old.source_amount);
    perform money._apply_balance_delta(old.destination_id, -old.destination_amount);

    update money.transfers t
    set time               = transfer_edit."time"::timestamp,
        source_id          = transfer_edit.source_id,
        source_amount      = transfer_edit.source_amount,
        destination_id     = transfer_edit.destination_id,
        destination_amount = transfer_edit.destination_amount,
        memo               = transfer_edit.memo
    where t.id = transfer_edit.id;

    perform money._apply_balance_delta(transfer_edit.source_id, -transfer_edit.source_amount);
    perform money._apply_balance_delta(transfer_edit.destination_id, transfer_edit.destination_amount);
end;
$$;

-- RPC: delete a transfer, reverting its balance effect. Idempotent.
create or replace function money.transfer_revert(id uuid) returns void
    language plpgsql
    set search_path = money
as
$$
declare
    old money.transfers;
begin
    delete from money.transfers t where t.id = transfer_revert.id returning * into old;

    if not found then
        return;
    end if;

    perform money._apply_balance_delta(old.source_id, old.source_amount);
    perform money._apply_balance_delta(old.destination_id, -old.destination_amount);
end;
$$;

-- RPC: daily prices as [[code, day, price], ...], ordered by day, from the given day.
-- Chunked, the client re-requests from the last received day.
create or replace function money.get_daily_prices_array(start_day_inclusive date default null) returns jsonb
    language sql
    stable
    set search_path = money
as
$$
select coalesce(jsonb_agg(jsonb_build_array(p.base_currency_code, p.day, p.price) order by p.day, p.base_currency_code),
                '[]'::jsonb)
from (select *
      from money.daily_prices dp
      where start_day_inclusive is null
         or dp.day >= start_day_inclusive
      order by dp.day, dp.base_currency_code
      limit 5000) p;
$$;

revoke all on all functions in schema money from public, anon;
grant execute on function
    money.atomic_crud(jsonb),
    money.transfer(uuid, uuid, numeric, uuid, numeric, text, text),
    money.transfer_edit(uuid, uuid, numeric, uuid, numeric, text, text),
    money.transfer_revert(uuid),
    money.get_daily_prices_array(date)
    to authenticated;
grant execute on function money._apply_balance_delta(uuid, numeric) to authenticated;

-- Seed currencies. USD is required by the client for totals.
insert into money.currencies (code, symbol, precision)
values ('EUR', '€', 2),
       ('USD', '$', 2),
       ('UAH', '₴', 2),
       ('RUB', '₽', 2),
       ('GBP', '£', 2),
       ('PLN', 'zł', 2),
       ('BTC', '₿', 8),
       ('ETH', 'Ξ', 18),
       ('USDT', '₮', 6)
on conflict (code) do nothing;

-- PowerSync replication: read-only role and a publication limited to `money`.
-- The role password is set separately, outside of the repo.
do
$$
    begin
        if not exists(select 1 from pg_roles where rolname = 'powersync_role') then
            create role powersync_role with replication bypassrls login;
        end if;
    end
$$;
grant usage on schema money to powersync_role;
grant select on all tables in schema money to powersync_role;
alter default privileges in schema money grant select on tables to powersync_role;

drop publication if exists powersync;
create publication powersync for table
    money.currencies,
    money.accounts,
    money.categories,
    money.transfers,
    money.sync_errors;
