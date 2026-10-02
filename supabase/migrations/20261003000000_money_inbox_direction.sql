-- Incoming bank payments (e.g. a salary) become incomes: an inbox item records
-- whether the money left the account or came to it. Existing items are outgoing.
alter table money.inbox_items
    add column direction text not null default 'outgoing'
        check (direction in ('outgoing', 'incoming'));
