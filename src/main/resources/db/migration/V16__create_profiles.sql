-- MediKiosk RW2: patient and physician profiles.
--
-- Editable demographic/profile data is kept SEPARATE from clinical records.
-- Clinical identity (who owns a case, the persisted intake answers, FHIR
-- export, review trail) lives on the users / completed_cases side; these
-- profiles hold mutable personal and professional data that a patient or
-- physician may update themselves, and that an administrator may maintain.
--
-- Profile changes are intentionally non-clinical: nothing clinical derives
-- from them, and no clinical record references a profile field.

CREATE TABLE IF NOT EXISTS patient_profiles (
    user_id                 BIGINT PRIMARY KEY REFERENCES users (id),
    date_of_birth           DATE,
    gender                  VARCHAR(20),
    phone                   VARCHAR(30),
    email                   VARCHAR(120),
    address_line1           VARCHAR(200),
    address_line2           VARCHAR(200),
    city                    VARCHAR(100),
    state                   VARCHAR(100),
    postal_code             VARCHAR(20),
    country                 VARCHAR(60),
    emergency_contact_name  VARCHAR(200),
    emergency_contact_phone VARCHAR(30),
    blood_group             VARCHAR(8),
    preferred_language      VARCHAR(16) NOT NULL DEFAULT 'en-IN',
    accessibility_prefs     VARCHAR(500),
    notification_prefs      VARCHAR(500),
    profile_picture_path    VARCHAR(500),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_patient_profile_gender CHECK (gender IN
        ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'))
);

CREATE TABLE IF NOT EXISTS physician_profiles (
    user_id              BIGINT PRIMARY KEY REFERENCES users (id),
    date_of_birth        DATE,
    phone                VARCHAR(30),
    email                VARCHAR(120),
    qualification        VARCHAR(300),
    designation          VARCHAR(200),
    department           VARCHAR(200),
    organization         VARCHAR(300),
    registration_number  VARCHAR(60),
    preferred_language   VARCHAR(16) NOT NULL DEFAULT 'en-IN',
    theme                VARCHAR(32) NOT NULL DEFAULT 'system',
    notification_prefs   VARCHAR(500),
    profile_picture_path VARCHAR(500),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);