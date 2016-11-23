CREATE DATABASE IF NOT EXISTS forums;
USE forums;

DROP TABLE IF EXISTS following;
DROP TABLE IF EXISTS subscription;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS thread;
DROP TABLE IF EXISTS forum;
DROP TABLE IF EXISTS user_profile;

CREATE TABLE user_profile (
  id          INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
  username    VARCHAR(50),
  email       VARCHAR(50)        NOT NULL,
  name        VARCHAR(50),
  about       TEXT,
  isAnonymous BOOLEAN            NOT NULL DEFAULT FALSE,
  UNIQUE KEY (email),
  KEY (name)
)
  DEFAULT CHARSET = utf8;

CREATE TABLE forum (
  id         INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
  name       VARCHAR(50) UNIQUE KEY,
  short_name VARCHAR(50)        NOT NULL,
  user_id    INT                NOT NULL,
  UNIQUE KEY (short_name),
  FOREIGN KEY (user_id) REFERENCES user_profile (id)
    ON DELETE CASCADE
)
  DEFAULT CHARSET = utf8;

CREATE TABLE thread (
  id            INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
  forum_id      INT                NOT NULL,
  title         VARCHAR(50)        NOT NULL,
  slug          VARCHAR(50)        NOT NULL,
  message       TEXT               NOT NULL,
  user_id       INT                NOT NULL,
  creation_time DATETIME           NOT NULL,
  likes         INT                NOT NULL DEFAULT 0,
  dislikes      INT                NOT NULL DEFAULT 0,
  isClosed      BOOLEAN            NOT NULL DEFAULT FALSE,
  isDeleted     BOOLEAN            NOT NULL DEFAULT FALSE,
  posts         INT                NOT NULL DEFAULT 0,
  FOREIGN KEY (forum_id) REFERENCES forum (id)
    ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES user_profile (id)
    ON DELETE CASCADE,
  KEY (user_id, creation_time),
  KEY (forum_id, creation_time)
)
  DEFAULT CHARSET = utf8;

CREATE TABLE post (
  id            INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
  user_id       INT                NOT NULL,
  message       TEXT               NOT NULL,
  forum_id      INT                NOT NULL,
  thread_id     INT                NOT NULL,
  parent        INT                NULL     DEFAULT NULL,
  creation_time DATETIME           NOT NULL,
  likes         INT                NOT NULL DEFAULT 0,
  dislikes      INT                NOT NULL DEFAULT 0,
  isApproved    BOOLEAN            NOT NULL DEFAULT FALSE,
  isHighlighted BOOLEAN            NOT NULL DEFAULT FALSE,
  isEdited      BOOLEAN            NOT NULL DEFAULT FALSE,
  isSpam        BOOLEAN            NOT NULL DEFAULT FALSE,
  isDeleted     BOOLEAN            NOT NULL DEFAULT FALSE,
  FOREIGN KEY (user_id) REFERENCES user_profile (id)
    ON DELETE CASCADE,
  FOREIGN KEY (forum_id) REFERENCES forum (id)
    ON DELETE CASCADE,
  FOREIGN KEY (thread_id) REFERENCES thread (id)
    ON DELETE CASCADE,
  KEY (user_id, creation_time),
  KEY (thread_id, creation_time),
  KEY (forum_id, creation_time),
  KEY (user_id, forum_id)
)
  DEFAULT CHARSET = utf8;

CREATE TABLE following (
  follower VARCHAR(50) NOT NULL,
  followee VARCHAR(50) NOT NULL,
  UNIQUE KEY (follower, followee),
  FOREIGN KEY (follower) REFERENCES user_profile (email)
    ON DELETE CASCADE,
  FOREIGN KEY (followee) REFERENCES user_profile (email)
    ON DELETE CASCADE
)
  DEFAULT CHARSET = utf8;

CREATE TABLE subscription (
  user_id   INT NOT NULL,
  thread_id INT NOT NULL,
  UNIQUE KEY (user_id, thread_id),
  FOREIGN KEY (user_id) REFERENCES user_profile (id)
    ON DELETE CASCADE,
  FOREIGN KEY (thread_id) REFERENCES thread (id)
    ON DELETE CASCADE
)
  DEFAULT CHARSET = utf8;