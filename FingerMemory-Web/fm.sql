CREATE TABLE `fm`.`fm_users`  (
  `user_id` int NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `user_name` varchar(20) NOT NULL COMMENT '用户名',
  `user_email` varchar(20) NOT NULL COMMENT '用户邮箱',
  `user_password` varchar(50) NOT NULL COMMENT '用户密码',
  `user_create_date` timestamp NOT NULL COMMENT '创建时间',
  `user_avatar_url` varchar(255) NULL COMMENT '用户头像URL',
  PRIMARY KEY (`user_id`),
  UNIQUE INDEX `user_name_index`(`user_name`) COMMENT '用户名唯一索引',
  UNIQUE INDEX `user_email_index`(`user_email`) COMMENT '用户邮箱唯一索引'
);

CREATE TABLE `fm`.`fm_books`  (
  `book_id` int NOT NULL AUTO_INCREMENT COMMENT '图书ID',
  `book_name` varchar(20) NOT NULL COMMENT '图书名',
  `book_author` int NOT NULL COMMENT '图书作者ID',
  `book_category` int NOT NULL COMMENT '图书类别ID',
  `book_baidescription` varchar(50) NULL COMMENT '图书描述',
  `book_cover_url` varchar(255) NULL COMMENT '图书封面URL',
  `book_create_date` timestamp NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`book_id`)
);

CREATE TABLE `fm`.`fm_book_categorys`  (
  `category_id` int NOT NULL COMMENT '分类ID',
  `category_name` varchar(20) NOT NULL COMMENT '分类名',
  PRIMARY KEY (`category_id`),
  UNIQUE INDEX `category_name_index`(`category_name`) COMMENT '分类名唯一索引'
);

CREATE TABLE `fm`.`fm_user_book_mapping`  (
  `user_id` int NOT NULL COMMENT '收藏者ID',
  `book_id` int NOT NULL COMMENT '图书ID',
  PRIMARY KEY (`user_id`, `book_id`)
);

CREATE TABLE `fm`.`fm_words`  (
  `word_id` int NOT NULL AUTO_INCREMENT COMMENT '单词ID',
  `word_english` varchar(20) NOT NULL COMMENT '单词英文',
  `word_translate` varchar(20) NOT NULL COMMENT '单词翻译',
  `word_baidescription` varchar(50) NULL COMMENT '单词描述',
  `word_cover_url` varchar(255) NULL COMMENT '单词图片URL',
  `word_category` int(255) NOT NULL COMMENT '单词类别ID',
  PRIMARY KEY (`word_id`)
);

CREATE TABLE `fm`.`fm_word_categorys`  (
  `category_id` int NOT NULL COMMENT '分类ID',
  `category_name` varchar(20) NOT NULL COMMENT '分类名',
  PRIMARY KEY (`category_id`),
  UNIQUE INDEX `category_name_index`(`category_name`) COMMENT '分类名唯一索引'
);

CREATE TABLE `fm`.`fm_notes`  (
  `note_id` int NOT NULL COMMENT '笔记ID',
  `word_id` int NOT NULL COMMENT '单词笔记ID',
  `note_author` int NOT NULL COMMENT '笔记作者ID',
  `note_baidescription` varchar(50) NULL COMMENT '笔记内容',
  `note_create_date` timestamp NULL COMMENT '笔记创建时间',
  `note_tag` varchar(20) NULL COMMENT '笔记标签',
  PRIMARY KEY (`note_id`, `word_id`, `note_author`)
);

CREATE TABLE `fm`.`fm_lexicons`  (
  `lexicon_id` int NOT NULL AUTO_INCREMENT COMMENT '词库ID',
  `lexicon_name` varchar(20) NOT NULL COMMENT '词库名',
  `lexicon_author` int(255) NOT NULL COMMENT '词库作者ID',
  `lexicon_baidescription` varchar(50) NULL COMMENT '词库描述',
  `lexicon_create_date` timestamp NOT NULL COMMENT '创建时间',
  `lexicon_cover_url` varchar(255) NULL COMMENT '词库封面URL',
  PRIMARY KEY (`lexicon_id`)
);

CREATE TABLE `fm`.`fm_user_lexicons`  (
  `user_id` int NOT NULL COMMENT '收藏者ID',
  `lexicon_id` int NOT NULL COMMENT '词库ID',
  PRIMARY KEY (`user_id`, `lexicon_id`)
);

CREATE TABLE `fm`.`fm_lexicon_word_mapping`  (
  `lexicon_id` int NOT NULL COMMENT '词库ID',
  `word_id` int NOT NULL COMMENT '单词ID',
  PRIMARY KEY (`lexicon_id`, `word_id`)
);

ALTER TABLE `fm`.`fm_books` 
ADD CONSTRAINT `fk_fm_books_author_user` FOREIGN KEY (`book_author`) REFERENCES `fm`.`fm_users` (`user_id`) ON DELETE NO ACTION ON UPDATE CASCADE,
ADD CONSTRAINT `fk_fm_books_book_category` FOREIGN KEY (`book_category`) REFERENCES `fm`.`fm_book_categorys` (`category_id`) ON DELETE NO ACTION ON UPDATE CASCADE;

ALTER TABLE `fm`.`fm_user_book_mapping` 
ADD CONSTRAINT `fk_fm_user_book_mapping_user_id` FOREIGN KEY (`user_id`) REFERENCES `fm`.`fm_users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
ADD CONSTRAINT `fk_fm_user_book_mapping_book_id` FOREIGN KEY (`book_id`) REFERENCES `fm`.`fm_books` (`book_id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `fm`.`fm_words` 
ADD CONSTRAINT `fk_fm_words_word_category` FOREIGN KEY (`word_category`) REFERENCES `fm`.`fm_word_categorys` (`category_id`) ON DELETE NO ACTION ON UPDATE CASCADE;

ALTER TABLE `fm`.`fm_notes` 
ADD CONSTRAINT `fk_fm_notes_note_word` FOREIGN KEY (`word_id`) REFERENCES `fm`.`fm_words` (`word_id`) ON DELETE CASCADE ON UPDATE CASCADE,
ADD CONSTRAINT `fk_fm_notes_note_author` FOREIGN KEY (`note_author`) REFERENCES `fm`.`fm_users` (`user_id`) ON DELETE NO ACTION ON UPDATE CASCADE;

ALTER TABLE `fm`.`fm_lexicons` 
ADD CONSTRAINT `fk_fm_lexicons_lexicon_author` FOREIGN KEY (`lexicon_author`) REFERENCES `fm`.`fm_users` (`user_id`) ON DELETE NO ACTION ON UPDATE CASCADE;

ALTER TABLE `fm`.`fm_user_lexicons` 
ADD CONSTRAINT `fk_fm_user_lexicons_mapping_user_id` FOREIGN KEY (`user_id`) REFERENCES `fm`.`fm_users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
ADD CONSTRAINT `fk_fm_user_lexicons_mapping_lexicon_id` FOREIGN KEY (`lexicon_id`) REFERENCES `fm`.`fm_lexicons` (`lexicon_id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `fm`.`fm_lexicon_word_mapping` 
ADD CONSTRAINT `fk_fm_lexicon_word_mapping_lexicon_id` FOREIGN KEY (`lexicon_id`) REFERENCES `fm`.`fm_lexicons` (`lexicon_id`) ON DELETE CASCADE ON UPDATE CASCADE,
ADD CONSTRAINT `fk_fm_lexicon_word_mapping_word_id` FOREIGN KEY (`word_id`) REFERENCES `fm`.`fm_words` (`word_id`) ON DELETE CASCADE ON UPDATE CASCADE;