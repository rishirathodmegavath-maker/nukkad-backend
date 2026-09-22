-- investor_intro_requests.investor_id (V88) was created with no foreign key at all — deleting an
-- investor already orphans any introduction requests referencing it (dangling id, no error, no
-- cleanup). Adding real bulk delete for the investor catalog would just multiply that, so this
-- adds the missing constraint first: deleting an investor now also removes its introduction
-- requests, at the DB level, for both the existing single-item delete and the new bulk delete.
--
-- Defensive: a row could already be orphaned under the no-FK regime (from a past single-investor
-- delete). Clean those up first so adding the constraint doesn't fail against existing bad data —
-- an orphaned request (its investor is already gone) has nothing left to close it out anyway.
DELETE FROM investor_intro_requests WHERE investor_id NOT IN (SELECT id FROM investors);

ALTER TABLE investor_intro_requests
    ADD CONSTRAINT fk_investor_intro_requests_investor
    FOREIGN KEY (investor_id) REFERENCES investors (id) ON DELETE CASCADE;
