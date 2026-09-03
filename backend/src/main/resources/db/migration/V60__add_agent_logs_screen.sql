UPDATE role_screen_config
  SET screen_ids = 'radar,cockpit,cr,bugs,uat,mandays,alerts,reports,capacity,clients,integrations,audit,admin,agent-builder,agent-logs'
  WHERE role_name = 'ADMIN';

UPDATE role_screen_config
  SET screen_ids = 'radar,cockpit,cr,bugs,uat,mandays,alerts,reports,capacity,clients,integrations,audit,agent-logs'
  WHERE role_name = 'HEAD_PJM';
