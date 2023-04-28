DROP TABLE IF EXISTS `trials`;
CREATE TABLE `trials`  (
  `owner_code` varchar(36) NOT NULL,
  `editor_code` varchar(36) NOT NULL,
  `viewer_code` varchar(36) NOT NULL,
  `trial` json NOT NULL,
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`owner_code`),
  INDEX(`owner_code`) USING BTREE,
  INDEX(`editor_code`) USING BTREE,
  INDEX(`viewer_code`) USING BTREE
);