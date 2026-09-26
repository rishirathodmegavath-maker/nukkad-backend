-- Startup Programs becomes content-managed: SPARK/IGNITE's copy (previously fixed in ProgramCatalog.java)
-- and their application question sets (previously fixed ProgramStep/ProgramField records) move into a
-- real `programs` table Admin can create/edit rows in, alongside the operational settings that already
-- lived in `program_settings`. `program_applications.program` keeps its exact existing string values
-- ('SPARK' / 'IGNITE') unchanged -- it now matches `programs.slug` by plain string equality (already
-- case-insensitive under this table's own utf8mb4_0900_ai_ci collation) rather than a fixed Java enum,
-- so zero existing application rows are touched by this migration.

CREATE TABLE programs (
  id                        CHAR(36)      NOT NULL,
  slug                      VARCHAR(60)   NOT NULL,
  name                      VARCHAR(120)  NOT NULL,
  badge                     VARCHAR(40)   NULL,
  tagline                   VARCHAR(220)  NOT NULL,
  description               TEXT          NOT NULL,
  hero_image_url            VARCHAR(500)  NULL,
  thumbnail_url             VARCHAR(500)  NULL,
  status                    VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
  display_order             INT           NOT NULL DEFAULT 0,
  outcome_heading           VARCHAR(200)  NULL,
  outcome_description       TEXT          NULL,
  audience_description      VARCHAR(500)  NULL,
  eligibility_title         VARCHAR(200)  NULL,
  eligibility_description   VARCHAR(500)  NULL,
  application_open          BOOLEAN       NOT NULL DEFAULT TRUE,
  fee_amount                INT           NULL,
  fee_currency              VARCHAR(10)   NULL,
  enrollment_info           VARCHAR(500)  NULL,
  selective                 BOOLEAN       NULL,
  highlights_json           JSON          NULL,
  target_audience_json      JSON          NULL,
  eligibility_points_json   JSON          NULL,
  journey_json              JSON          NULL,
  benefits_json             JSON          NULL,
  application_steps_json    JSON          NULL,
  created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_programs_slug (slug),
  KEY idx_programs_status_order (status, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Widened from 20: an admin-chosen slug for a new program is no longer bound to a fixed two-value
-- enum's name() and can be longer than "SPARK"/"IGNITE". Existing values are untouched by this.
ALTER TABLE program_applications MODIFY COLUMN program VARCHAR(60) NOT NULL;

INSERT IGNORE INTO programs (
  id, slug, name, tagline, description, status, display_order,
  outcome_description, highlights_json, target_audience_json, journey_json, benefits_json, application_steps_json
) VALUES (
  UUID(), 'SPARK', 'SPARK',
  'Build your path to entrepreneurship.',
  'For people who want to explore entrepreneurship and discover what they want to build — learn from founders and experts, explore opportunities and industries, and take your first steps.',
  'PUBLISHED', 0,
  'Move from curiosity toward a clear business direction.',
  JSON_ARRAY('Explore entrepreneurship', 'Learn from founders and experts', 'Explore opportunities and industries', 'Build a builder community', 'Develop and validate ideas', 'Take first steps'),
  JSON_ARRAY('Students', 'Working professionals', 'People exploring entrepreneurship'),
  JSON_ARRAY(
    JSON_OBJECT('number', 1, 'title', 'Think', 'description', 'Understand entrepreneurship.'),
    JSON_OBJECT('number', 2, 'title', 'Explore', 'description', 'Find opportunities and problems worth pursuing.'),
    JSON_OBJECT('number', 3, 'title', 'Ideate', 'description', 'Work on ideas and get feedback.'),
    JSON_OBJECT('number', 4, 'title', 'Validate', 'description', 'Test ideas with mentors and users.'),
    JSON_OBJECT('number', 5, 'title', 'Build', 'description', 'Take first steps toward a business.')
  ),
  JSON_ARRAY(
    JSON_OBJECT('order', 0, 'title', 'Live sessions'),
    JSON_OBJECT('order', 1, 'title', 'Founder/expert sessions'),
    JSON_OBJECT('order', 2, 'title', 'Builder community'),
    JSON_OBJECT('order', 3, 'title', 'Idea exploration'),
    JSON_OBJECT('order', 4, 'title', 'Resources/templates'),
    JSON_OBJECT('order', 5, 'title', 'Peer discussions'),
    JSON_OBJECT('order', 6, 'title', '1:1 guidance'),
    JSON_OBJECT('order', 7, 'title', 'Demo/showcase day')
  ),
  JSON_ARRAY(
    JSON_OBJECT('id', 'basic-information', 'title', 'Basic Information', 'fields', JSON_ARRAY(
      JSON_OBJECT('key', 'fullName', 'label', 'Full Name', 'type', 'TEXT', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'email', 'label', 'Email Address', 'type', 'EMAIL', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'phone', 'label', 'Phone Number', 'type', 'PHONE', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'city', 'label', 'City / Location', 'type', 'TEXT', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'dateOfBirth', 'label', 'Date of Birth', 'type', 'DATE', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'currentStatus', 'label', 'Current Status', 'type', 'SELECT', 'required', TRUE, 'options', JSON_ARRAY('Student', 'Working Professional', 'Exploring Entrepreneurship', 'Other'))
    )),
    JSON_OBJECT('id', 'background', 'title', 'Background', 'fields', JSON_ARRAY(
      JSON_OBJECT('key', 'educationLevel', 'label', 'Highest Education Level', 'type', 'SELECT', 'required', TRUE, 'options', JSON_ARRAY('High School', 'Undergraduate', 'Graduate', 'Postgraduate', 'Other')),
      JSON_OBJECT('key', 'institution', 'label', 'College / School / Organization', 'type', 'TEXT', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'fieldOfStudyOrWork', 'label', 'Field of Study / Work', 'type', 'TEXT', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'currentYear', 'label', 'Current Year / Graduation Year', 'type', 'TEXT', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'aboutYourself', 'label', 'Tell us a bit about yourself', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY())
    )),
    JSON_OBJECT('id', 'interests', 'title', 'Interests', 'fields', JSON_ARRAY(
      JSON_OBJECT('key', 'interests', 'label', 'What are you interested in?', 'type', 'MULTISELECT', 'required', FALSE, 'options', JSON_ARRAY('Technology', 'Healthcare', 'Education', 'Consumer Brands', 'Fintech', 'Sustainability', 'AI / Deep Tech', 'Social Impact', 'Sports', 'E-commerce', 'Creators / Media', 'Other')),
      JSON_OBJECT('key', 'topicsToLearn', 'label', 'What topics would you like to learn more about?', 'type', 'TEXTAREA', 'required', FALSE, 'options', JSON_ARRAY())
    )),
    JSON_OBJECT('id', 'goals', 'title', 'Goals', 'fields', JSON_ARRAY(
      JSON_OBJECT('key', 'whyJoin', 'label', 'Why do you want to join SPARK?', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'sixToTwelveMonthGoal', 'label', 'What do you hope to achieve in the next 6–12 months?', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'threeToFiveYearVision', 'label', 'Where do you see yourself in 3–5 years?', 'type', 'TEXTAREA', 'required', FALSE, 'options', JSON_ARRAY())
    )),
    JSON_OBJECT('id', 'short-answers', 'title', 'Short Answers', 'fields', JSON_ARRAY(
      JSON_OBJECT('key', 'excitesYouMost', 'label', 'What excites you most about entrepreneurship?', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'existingIdeas', 'label', 'Do you have any startup/business ideas right now?', 'type', 'TEXTAREA', 'required', FALSE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'skillsAndStrengths', 'label', 'What skills or strengths do you bring?', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'anythingElse', 'label', 'Is there anything else you''d like us to know?', 'type', 'TEXTAREA', 'required', FALSE, 'options', JSON_ARRAY())
    )),
    JSON_OBJECT('id', 'review', 'title', 'Review & Submit', 'fields', JSON_ARRAY())
  )
);

INSERT IGNORE INTO programs (
  id, slug, name, tagline, description, status, display_order,
  outcome_description, highlights_json, target_audience_json, journey_json, benefits_json, application_steps_json
) VALUES (
  UUID(), 'IGNITE', 'IGNITE',
  'Turn your idea into a real, investable business.',
  'For early-stage founders with a business idea who want to validate it, build an MVP, get first users, and work with mentors toward a fundable, scalable business.',
  'PUBLISHED', 1,
  'Move from idea toward a validated, scalable, investable business.',
  JSON_ARRAY('Validate an idea', 'Build MVP', 'Get first users', 'Work with mentors', 'Learn GTM/revenue/growth', 'Prepare for fundraising'),
  JSON_ARRAY('Early-stage founders', 'People with a startup idea', 'Builders working toward MVP', 'Founders preparing for growth/fundraising'),
  JSON_ARRAY(
    JSON_OBJECT('number', 1, 'title', 'Idea', 'description', 'Start with a real problem and opportunity.'),
    JSON_OBJECT('number', 2, 'title', 'Validate', 'description', 'Test with users and get evidence.'),
    JSON_OBJECT('number', 3, 'title', 'Vibe Code', 'description', 'Build a functional prototype using AI tools.'),
    JSON_OBJECT('number', 4, 'title', 'MVP', 'description', 'Launch MVP and get first users.'),
    JSON_OBJECT('number', 5, 'title', 'Users', 'description', 'Acquire and understand users.'),
    JSON_OBJECT('number', 6, 'title', 'Business', 'description', 'Build a scalable business model.'),
    JSON_OBJECT('number', 7, 'title', 'Growth', 'description', 'Scale with GTM, product and team.'),
    JSON_OBJECT('number', 8, 'title', 'Fundraising', 'description', 'Prepare and raise capital.')
  ),
  JSON_ARRAY(
    JSON_OBJECT('order', 0, 'title', 'Live sessions'),
    JSON_OBJECT('order', 1, 'title', 'Vibe coding / rapid prototyping'),
    JSON_OBJECT('order', 2, 'title', 'Industry mentor sessions'),
    JSON_OBJECT('order', 3, 'title', '1:1 founder support'),
    JSON_OBJECT('order', 4, 'title', 'GTM & growth strategy'),
    JSON_OBJECT('order', 5, 'title', 'Resources & templates'),
    JSON_OBJECT('order', 6, 'title', 'Demo day'),
    JSON_OBJECT('order', 7, 'title', 'Exclusive community')
  ),
  JSON_ARRAY(
    JSON_OBJECT('id', 'basic-information', 'title', 'Basic Information', 'fields', JSON_ARRAY(
      JSON_OBJECT('key', 'fullName', 'label', 'Full Name', 'type', 'TEXT', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'email', 'label', 'Email', 'type', 'EMAIL', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'phone', 'label', 'Phone', 'type', 'PHONE', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'city', 'label', 'City / Location', 'type', 'TEXT', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'dateOfBirth', 'label', 'Date of Birth', 'type', 'DATE', 'required', TRUE, 'options', JSON_ARRAY())
    )),
    JSON_OBJECT('id', 'background', 'title', 'Background', 'fields', JSON_ARRAY(
      JSON_OBJECT('key', 'educationLevel', 'label', 'Highest Education', 'type', 'SELECT', 'required', FALSE, 'options', JSON_ARRAY('High School', 'Undergraduate', 'Graduate', 'Postgraduate', 'Other')),
      JSON_OBJECT('key', 'institution', 'label', 'College / Organization', 'type', 'TEXT', 'required', FALSE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'fieldOfStudyOrWork', 'label', 'Field of Study / Work', 'type', 'TEXT', 'required', FALSE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'currentRole', 'label', 'Current Role', 'type', 'TEXT', 'required', FALSE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'aboutYourself', 'label', 'About yourself', 'type', 'TEXTAREA', 'required', FALSE, 'options', JSON_ARRAY())
    )),
    JSON_OBJECT('id', 'startup-idea', 'title', 'Startup / Idea', 'fields', JSON_ARRAY(
      JSON_OBJECT('key', 'whatBuilding', 'label', 'What are you building?', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'problem', 'label', 'What problem are you solving?', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'targetCustomer', 'label', 'Who is your target customer?', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'demoLink', 'label', 'Website/demo link', 'type', 'URL', 'required', FALSE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'hasCofounder', 'label', 'Do you have a co-founder?', 'type', 'SELECT', 'required', FALSE, 'options', JSON_ARRAY('Yes', 'No')),
      JSON_OBJECT('key', 'cofounderDetails', 'label', 'Co-founder details (if applicable)', 'type', 'TEXTAREA', 'required', FALSE, 'options', JSON_ARRAY())
    )),
    JSON_OBJECT('id', 'progress', 'title', 'Progress', 'fields', JSON_ARRAY(
      JSON_OBJECT('key', 'stage', 'label', 'What stage are you at?', 'type', 'SELECT', 'required', TRUE, 'options', JSON_ARRAY('Idea', 'Prototype', 'MVP', 'Early Users', 'Revenue', 'Scaling')),
      JSON_OBJECT('key', 'whatBuiltSoFar', 'label', 'What have you built so far?', 'type', 'TEXTAREA', 'required', FALSE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'hasUsersOrCustomers', 'label', 'Do you have users/customers?', 'type', 'SELECT', 'required', FALSE, 'options', JSON_ARRAY('Yes', 'No')),
      JSON_OBJECT('key', 'approxUserCount', 'label', 'Approximate number of users/customers', 'type', 'TEXT', 'required', FALSE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'validationCompleted', 'label', 'What validation have you completed?', 'type', 'TEXTAREA', 'required', FALSE, 'options', JSON_ARRAY())
    )),
    JSON_OBJECT('id', 'goals', 'title', 'Goals', 'fields', JSON_ARRAY(
      JSON_OBJECT('key', 'programGoals', 'label', 'What do you want to achieve during the program?', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'biggestChallenge', 'label', 'What is your biggest challenge right now?', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'supportNeeded', 'label', 'What kind of support do you need?', 'type', 'TEXTAREA', 'required', FALSE, 'options', JSON_ARRAY()),
      JSON_OBJECT('key', 'whySelected', 'label', 'Why should you be selected?', 'type', 'TEXTAREA', 'required', TRUE, 'options', JSON_ARRAY())
    )),
    JSON_OBJECT('id', 'review', 'title', 'Review & Submit', 'fields', JSON_ARRAY())
  )
);

-- Carries over whatever Admin had already set in the old settings table (both rows are the
-- application_open=TRUE / everything-else-null default today, but this copies real values, not that
-- default, so it stays correct even if that changes before this migration ships).
UPDATE programs p
JOIN program_settings s ON s.program = p.slug
SET p.application_open = s.application_open,
    p.fee_amount = s.fee_amount,
    p.fee_currency = s.fee_currency,
    p.enrollment_info = s.enrollment_info,
    p.selective = s.selective;

DROP TABLE program_settings;
