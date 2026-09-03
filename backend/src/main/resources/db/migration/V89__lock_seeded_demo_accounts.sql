-- Security (audit C1): the csm@ / revenue@ accounts seeded in V62 copied the
-- bootstrap admin's password hash, so they share a known/published credential.
-- Lock their passwords to a non-verifiable sentinel (bcrypt.matches() -> false)
-- so the known password no longer works. An admin must set a real password (or
-- re-provision) via the user console before these accounts can log in.
UPDATE app_users
   SET password = 'LOCKED-' || gen_random_uuid()
 WHERE email IN ('csm@orbit.io', 'revenue@orbit.io');
