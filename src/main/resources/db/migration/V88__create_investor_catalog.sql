-- Investor Discovery: an admin-managed investor catalog, separate from the pre-existing, user-owned
-- investor_profiles (self-serve accounts created via investor_activation_requests — untouched by this
-- migration and still used for the two-way introduction workflow). A founder never creates a row here;
-- only an admin does, via /api/admin/investor-catalog. See the Investor entity's class comment.

CREATE TABLE investors (
    id                          CHAR(36)      NOT NULL PRIMARY KEY,
    name                        VARCHAR(200)  NOT NULL,
    investor_type               VARCHAR(20)   NOT NULL,
    description                 TEXT          NULL,
    location                    VARCHAR(200)  NULL,
    website                     VARCHAR(300)  NULL,
    logo_url                    VARCHAR(500)  NULL,
    cheque_min                  BIGINT        NULL,
    cheque_max                  BIGINT        NULL,
    active                      BOOLEAN       NOT NULL DEFAULT TRUE,
    visible                     BOOLEAN       NOT NULL DEFAULT TRUE,
    -- Optional bridge to a real, activated investor account (investor_profiles.id) — see the Investor
    -- entity's class comment. Deliberately no FK: investor_profiles rows can be deleted independently
    -- (InvestorProfileService#delete) and this is app-level routing metadata, not referential data.
    linked_investor_profile_id  CHAR(36)      NULL,
    created_by_admin_id         CHAR(36)      NOT NULL,
    created_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_investors_active_visible (active, visible),
    INDEX idx_investors_type (investor_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investor_sectors (
    investor_id CHAR(36)     NOT NULL,
    sector      VARCHAR(255) NOT NULL,
    CONSTRAINT fk_investor_sectors_investor FOREIGN KEY (investor_id) REFERENCES investors (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investor_stages (
    investor_id CHAR(36)     NOT NULL,
    stage       VARCHAR(255) NOT NULL,
    CONSTRAINT fk_investor_stages_investor FOREIGN KEY (investor_id) REFERENCES investors (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A founder's introduction request to a catalog investor that has no linked live account. When one is linked,
-- a request instead goes through the existing intro_requests table untouched — this table only ever holds the
-- "no live account to notify" case (see InvestorCatalogService#requestIntroduction).
CREATE TABLE investor_intro_requests (
    id                 CHAR(36)     NOT NULL PRIMARY KEY,
    investor_id        CHAR(36)     NOT NULL,
    requester_user_id  CHAR(36)     NOT NULL,
    startup_id         CHAR(36)     NOT NULL,
    message            VARCHAR(1000) NOT NULL,
    status             VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    closed_by_admin_id CHAR(36)     NULL,
    closed_at          TIMESTAMP    NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_investor_intro_requests_investor_startup_status (investor_id, startup_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
