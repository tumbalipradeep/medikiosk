-- MediKiosk RW2: default runtime system settings.
--
-- These are administrative configuration records exposed by the admin console.
-- Values are stored as VARCHAR and interpreted by the application layer; the
-- language selector is validated against the supported-language model at the
-- service boundary, so an unsupported language can never be stored.

INSERT INTO system_settings (setting_key, setting_value, description, updated_at) VALUES
    ('kiosk.display_name', 'MediKiosk',
     'Display name shown to patients and staff.', now()),
    ('kiosk.default_language', 'en-IN',
     'Default patient language. Must be one of the supported language codes.', now()),
    ('kiosk.maintenance_mode', 'false',
     'When true, the deployment is marked as under maintenance in the admin console.', now()),
    ('kiosk.support_email', 'support@medikiosk.local',
     'Support contact address shown in deployment documentation.', now()),
    ('ai.preferred_provider', 'groq',
     'Preferred conversational AI provider recorded for operators. The effective failover order is the configured runtime order.', now());
