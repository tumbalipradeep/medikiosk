-- MediKiosk RD2: server-side display preferences for authenticated users.
--
-- Display preferences (theme / motion / text size) were previously
-- browser-local only (localStorage). This table adds an authenticated-user
-- persistence layer so a returning user's preferences can seed the
-- pre-paint state on any device. Anonymous users remain localStorage-only:
-- no cookies, no fingerprinting, no anonymous rows.
--
-- Values are closed sets enforced here and in the service layer:
--   theme     system | light   | dark
--   motion    dynamic | standard | reduced
--   text_size standard | large  | xlarge

CREATE TABLE IF NOT EXISTS user_display_preferences (
    user_id    BIGINT PRIMARY KEY REFERENCES users (id),
    theme      VARCHAR(16) NOT NULL DEFAULT 'system',
    motion     VARCHAR(16) NOT NULL DEFAULT 'dynamic',
    text_size  VARCHAR(16) NOT NULL DEFAULT 'standard',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_user_pref_theme CHECK (theme IN ('system', 'light', 'dark')),
    CONSTRAINT ck_user_pref_motion CHECK (motion IN ('dynamic', 'standard', 'reduced')),
    CONSTRAINT ck_user_pref_text_size CHECK (text_size IN ('standard', 'large', 'xlarge'))
);
