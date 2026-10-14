-- Adds the opaque key for a profile image stored outside the database and static web root.
ALTER TABLE `user` ADD COLUMN avatar_key VARCHAR(64) NULL;
