DROP SCHEMA if EXISTS crawldb cascade;

create schema if not exists crawldb;

create table crawldb.data_type
(
  code varchar(20) not null,
  constraint pk_data_type_code primary key (code)
);

create table crawldb.page_type
(
  code varchar(20) not null,
  constraint pk_page_type_code primary key (code)
);

create table crawldb.site
(
  id              serial not null,
  "domain"        varchar(500),
  robots_content  text,
  sitemap_content text,
  constraint pk_site_id primary key (id),
  constraint unq_domain_idx unique ("domain")
);

create table crawldb.page
(
  id               serial not null,
  site_id          integer,
  page_type_code   varchar(20),
  url              varchar(3000),
  hash             varchar(256),
  html_content     text,
  http_status_code integer,
  load_time        bigint,
  accessed_time    timestamp,
  stored_time      timestamp,
  constraint pk_page_id primary key (id),
  constraint unq_url_idx unique (url)
);

create index "idx_page_site_id" on crawldb.page (site_id);

create index "idx_page_page_type_code" on crawldb.page (page_type_code);

create table crawldb.page_data
(
  id             serial not null,
  page_id        integer,
  filename       varchar(255),
  data_type_code varchar(20),
  "data"         bytea,
  constraint pk_page_data_id primary key (id)
);

create index "idx_page_data_page_id" on crawldb.page_data (page_id);

create index "idx_page_data_data_type_code" on crawldb.page_data (data_type_code);

create table crawldb.image
(
  id            serial not null,
  page_id       integer,
  filename      varchar(255),
  content_type  varchar(50),
  "data"        bytea,
  accessed_time timestamp,
  constraint pk_image_id primary key (id)
);

create index "idx_image_page_id" on crawldb.image (page_id);

create table crawldb.link
(
  from_page integer not null,
  to_page   integer not null,
  constraint _0 primary key (from_page, to_page)
);

create index "idx_link_from_page" on crawldb.link (from_page);

create index "idx_link_to_page" on crawldb.link (to_page);

alter TABLE crawldb.image
  ADD CONSTRAINT fk_image_page_data FOREIGN KEY (page_id) REFERENCES crawldb.page (id) ON delete RESTRICT;

alter TABLE crawldb.link
  ADD CONSTRAINT fk_link_page FOREIGN KEY (from_page) REFERENCES crawldb.page (id) ON delete RESTRICT;

alter TABLE crawldb.link
  ADD CONSTRAINT fk_link_page_1 FOREIGN KEY (to_page) REFERENCES crawldb.page (id) ON delete RESTRICT;

alter TABLE crawldb.page
  ADD CONSTRAINT fk_page_site FOREIGN KEY (site_id) REFERENCES crawldb.site (id) ON delete RESTRICT;

alter TABLE crawldb.page
  ADD CONSTRAINT fk_page_page_type FOREIGN KEY (page_type_code) REFERENCES crawldb.page_type (code) ON delete RESTRICT;

alter TABLE crawldb.page_data
  ADD CONSTRAINT fk_page_data_page FOREIGN KEY (page_id) REFERENCES crawldb.page (id) ON delete RESTRICT;

alter TABLE crawldb.page_data
  ADD CONSTRAINT fk_page_data_data_type FOREIGN KEY (data_type_code) REFERENCES crawldb.data_type (code) ON delete RESTRICT;

insert into crawldb.data_type
values ('PDF'),
       ('DOC'),
       ('DOCX'),
       ('PPT'),
       ('PPTX');

insert into crawldb.page_type
values ('HTML'),
       ('BINARY'),
       ('DUPLICATE'),
       ('FRONTIER'),
       ('INVALID'),
       ('DISALLOWED');

-- insert into crawldb.site
-- values (1, 'site1', 'robots', 'sitemap');
