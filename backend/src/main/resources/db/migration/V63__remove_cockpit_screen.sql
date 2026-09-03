UPDATE role_screen_config
  SET screen_ids = REPLACE(screen_ids, 'cockpit,', '')
  WHERE screen_ids LIKE '%cockpit,%';

UPDATE role_screen_config
  SET screen_ids = REPLACE(screen_ids, ',cockpit', '')
  WHERE screen_ids LIKE '%,cockpit%';
