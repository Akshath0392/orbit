-- Replace jira/darwin with unified integrations screen, add agent-builder
UPDATE role_screen_config
  SET screen_ids = 'radar,cockpit,cr,bugs,uat,mandays,alerts,reports,capacity,clients,integrations,audit,admin,agent-builder'
  WHERE role_name = 'ADMIN';

UPDATE role_screen_config
  SET screen_ids = 'radar,cockpit,cr,bugs,uat,mandays,alerts,reports,capacity,clients,integrations,audit'
  WHERE role_name = 'HEAD_PJM';

UPDATE role_screen_config
  SET screen_ids = 'cockpit,cr,bugs,uat,mandays,alerts,reports,capacity,clients,integrations'
  WHERE role_name = 'PJM';
