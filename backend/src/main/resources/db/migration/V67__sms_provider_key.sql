-- V67: The SMS provider (mNotify) API key can now be entered by the Super Admin on the SMS page
-- instead of only through the server's .env. Stored encrypted, like every other secret.
ALTER TABLE sms_platform_settings ADD COLUMN mnotify_api_key TEXT;
