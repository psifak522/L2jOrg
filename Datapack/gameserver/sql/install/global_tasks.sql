DROP TABLE IF EXISTS `global_tasks`;
CREATE TABLE IF NOT EXISTS `global_tasks` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL DEFAULT '',
  `type` VARCHAR(50) NOT NULL DEFAULT '',
  `last_activation` BIGINT NOT NULL DEFAULT '0',
  `param1` VARCHAR(100) NOT NULL DEFAULT '',
  `param2` VARCHAR(100) NOT NULL DEFAULT '',
  `param3` VARCHAR(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
)  ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;