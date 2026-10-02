-- 4Money: payee rules by amount and "ask me" rules.
-- A payee may have several rules with amount ranges (a snack vs. fuel at the same station)
-- and one plain rule without a range. A rule either records to its category or asks the user
-- in a notification, then it has no category.
--
-- Apply BEFORE installing an app build that writes these columns.
-- Older builds keep working: they read only the old columns, and every existing row
-- stays a plain recording rule (both edges null, action 'record').

alter table money.payee_rules
    -- Payment amounts in the payment currency, major units, like inbox_items.amount.
    -- A null edge is unbounded; both null means any amount.
    add column min_amount   numeric,
    add column max_amount   numeric,
    -- Interval notation for the edges: '[)' = min inclusive, max exclusive.
    add column range_bounds text not null default '[)'
        check (range_bounds in ('[)', '[]', '(]', '()')),
    add column action       text not null default 'record'
        check (action in ('record', 'ask')),
    add constraint payee_rules_range_order_check
        check (min_amount is null or max_amount is null or min_amount <= max_amount);

-- An 'ask' rule has no category.
alter table money.payee_rules
    alter column category_id drop not null,
    add constraint payee_rules_target_check
        check (action = 'ask' or category_id is not null);
