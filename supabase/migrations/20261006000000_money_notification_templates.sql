-- 4Money: user-taught bank notification templates (auto-booking sources, F4).
-- A template is a generated regex plus the capture group numbers of its fields, matched on
-- the device against title + "\n" + text of a notification of source_package.
-- Per-user table like payee_rules: user_id defaults to auth.uid(), RLS on.
--
-- Apply this migration BEFORE installing a build of this branch: the app uploads rows to
-- money.notification_templates through atomic_crud, which must already allow the table.
-- CREATE OR REPLACE of atomic_crud keeps the existing grants (execute to authenticated only).

create table money.notification_templates
(
    id             uuid primary key,
    user_id        uuid      not null default auth.uid() references auth.users (id) on delete cascade,
    source_package text      not null check (source_package <> ''),
    name           text      not null,
    direction      text      not null default 'outgoing' check (direction in ('outgoing', 'incoming')),
    -- Generated regex (Java/Kotlin syntax), matched against title + "\n" + text.
    pattern        text      not null check (pattern <> ''),
    -- Capture group numbers: {"amount":1,"currency":2,"payee":3,"card":4,"hasTimestamp":true}.
    fields         jsonb     not null,
    sample_text    text      not null,
    is_enabled     boolean   not null default true,
    -- Local wall-clock time, no time zone, like transfers.time.
    created_at     timestamp not null
);
create index notification_templates_user_idx on money.notification_templates (user_id);

alter table money.notification_templates enable row level security;

create policy notification_templates_own on money.notification_templates
    for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

grant select, insert, update, delete on money.notification_templates to authenticated;
grant all on money.notification_templates to service_role;
grant select on money.notification_templates to powersync_role;

alter publication powersync add table money.notification_templates;

-- Same body as in 20261004000000_money_upstream_parity.sql, only the writable table list is extended.
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

                if table_name not in ('accounts', 'categories', 'transfers', 'payee_rules', 'inbox_items', 'notification_templates') then
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
