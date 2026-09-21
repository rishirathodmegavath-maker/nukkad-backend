-- The "Create a Post" dialog: seven more post kinds a member can pick (feedback, cofounder, announcement,
-- resource, hiring, fundraising, product_launch), who may see a post (visibility), an optional link, and Word /
-- PowerPoint / Excel files next to PDFs (attachment kind FILE).
-- Existing rows are untouched: they keep their type, stay PUBLIC and have no link.
ALTER TABLE posts
  MODIFY COLUMN type ENUM('text','startup_update','idea','opportunity','event','discussion','build_update','question','milestone',
                          'feedback','cofounder','announcement','resource','hiring','fundraising','product_launch')
    NOT NULL DEFAULT 'text',
  ADD COLUMN visibility ENUM('PUBLIC','CONNECTIONS') NOT NULL DEFAULT 'PUBLIC',
  ADD COLUMN link_url   VARCHAR(500) NULL;

ALTER TABLE post_attachments
  MODIFY COLUMN kind ENUM('IMAGE','VIDEO','PDF','FILE') NOT NULL;
