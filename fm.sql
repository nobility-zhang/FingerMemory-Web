CREATE TABLE `fm`.`fm_users`  (
  `user_id` int NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `user_name` varchar(20) NOT NULL COMMENT '用户名',
  `user_email` varchar(50) NOT NULL COMMENT '用户邮箱',
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
  `category_id` int NOT NULL AUTO_INCREMENT COMMENT '分类ID',
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
  `category_id` int NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_name` varchar(20) NOT NULL COMMENT '分类名',
  PRIMARY KEY (`category_id`),
  UNIQUE INDEX `category_name_index`(`category_name`) COMMENT '分类名唯一索引'
);

CREATE TABLE `fm`.`fm_notes`  (
  `note_id` int NOT NULL AUTO_INCREMENT COMMENT '笔记ID',
  `word_id` int NOT NULL COMMENT '单词ID',
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

INSERT INTO fm_users ( user_name, user_email, user_password, user_create_date, user_avatar_url )
VALUES
	( 'zhangsan', '10000000@qq.com', '123456', FROM_UNIXTIME( 1614127703, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=20' ),
	( 'zhangsan0', '10000001@qq.com', '123456', FROM_UNIXTIME( 1614127803, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=0' ),
	( 'zhangsan1', '10000002@qq.com', '123456', FROM_UNIXTIME( 1614127903, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=1' ),
	( 'zhangsan2', '10000003@qq.com', '123456', FROM_UNIXTIME( 1614128003, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=2' ),
	( 'zhangsan3', '10000004@qq.com', '123456', FROM_UNIXTIME( 1614128103, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=3' ),
	( 'zhangsan4', '10000005@qq.com', '123456', FROM_UNIXTIME( 1614128203, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=4' ),
	( 'zhangsan5', '10000006@qq.com', '123456', FROM_UNIXTIME( 1614128303, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=5' ),
	( 'zhangsan6', '10000007@qq.com', '123456', FROM_UNIXTIME( 1614128403, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=6' ),
	( 'zhangsan7', '10000008@qq.com', '123456', FROM_UNIXTIME( 1614128503, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=7' ),
	( 'zhangsan8', '10000009@qq.com', '123456', FROM_UNIXTIME( 1614128603, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=8' ),
	( 'zhangsan9', '10000010@qq.com', '123456', FROM_UNIXTIME( 1614128703, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=9' ),
	( 'zhangsan10', '10000011@qq.com', '123456', FROM_UNIXTIME( 1614128803, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=10' ),
	( 'zhangsan11', '10000012@qq.com', '123456', FROM_UNIXTIME( 1614128903, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=11' ),
	( 'zhangsan12', '10000013@qq.com', '123456', FROM_UNIXTIME( 1614129003, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=12' ),
	( 'zhangsan13', '10000014@qq.com', '123456', FROM_UNIXTIME( 1614129103, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=13' ),
	( 'zhangsan14', '10000015@qq.com', '123456', FROM_UNIXTIME( 1614129203, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=14' ),
	( 'zhangsan15', '10000016@qq.com', '123456', FROM_UNIXTIME( 1614129303, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=15' ),
	( 'zhangsan16', '10000017@qq.com', '123456', FROM_UNIXTIME( 1614129403, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=16' ),
	( 'zhangsan17', '10000018@qq.com', '123456', FROM_UNIXTIME( 1614129503, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=17' ),
	( 'zhangsan18', '10000019@qq.com', '123456', FROM_UNIXTIME( 1614129603, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=18' ),
	( 'zhangsan19', '10000020@qq.com', '123456', FROM_UNIXTIME( 1614129703, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/g/30/30?image=19' );

INSERT INTO fm_book_categorys ( category_name )
VALUES
	( '计算机理论与方法' ),
	( '计算机软件' ),
	( '微型计算机' ),
	( '电子商务' ),
	( '编程与开发' ),
	( '办公与计算机' ),
	( '数据库' ),
	( '家庭与计算机' ),
	( '考试与认证' ),
	( 'IT产业与文化' );

INSERT INTO fm_word_categorys ( category_name )
VALUES
	( '变量名' ),
	( '命令' ),
	( '计算机常用名词' ),
	( '计算机常用量词' ),
	( '计算机专业术语' );

INSERT INTO fm_books ( book_name, book_author, book_category, book_baidescription, book_cover_url, book_create_date )
VALUES
	( '张三的测试图书', 1, 1, '张三的测试图书描述，张三的测试图书描述，张三的测试图书描述', 'https://placekitten.com/400/600??image=20', FROM_UNIXTIME( 1614127703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书0', 5, 3, '张三的测试图书描述0，张三的测试图书描述0，张三的测试图书描述1', 'https://placekitten.com/400/600?image=0', FROM_UNIXTIME( 1614127703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书1', 9, 5, '张三的测试图书描述1，张三的测试图书描述1，张三的测试图书描述1', 'https://placekitten.com/400/600?image=1', FROM_UNIXTIME( 1614128703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书2', 9, 5, '张三的测试图书描述2，张三的测试图书描述2，张三的测试图书描述1', 'https://placekitten.com/400/600?image=2', FROM_UNIXTIME( 1614129703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书3', 8, 5, '张三的测试图书描述3，张三的测试图书描述3，张三的测试图书描述1', 'https://placekitten.com/400/600?image=3', FROM_UNIXTIME( 1614130703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书4', 9, 1, '张三的测试图书描述4，张三的测试图书描述4，张三的测试图书描述1', 'https://placekitten.com/400/600?image=4', FROM_UNIXTIME( 1614131703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书5', 1, 3, '张三的测试图书描述5，张三的测试图书描述5，张三的测试图书描述1', 'https://placekitten.com/400/600?image=5', FROM_UNIXTIME( 1614132703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书6', 2, 9, '张三的测试图书描述6，张三的测试图书描述6，张三的测试图书描述1', 'https://placekitten.com/400/600?image=6', FROM_UNIXTIME( 1614133703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书7', 8, 2, '张三的测试图书描述7，张三的测试图书描述7，张三的测试图书描述1', 'https://placekitten.com/400/600?image=7', FROM_UNIXTIME( 1614134703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书8', 5, 10, '张三的测试图书描述8，张三的测试图书描述8，张三的测试图书描述1', 'https://placekitten.com/400/600?image=8', FROM_UNIXTIME( 1614135703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书9', 3, 10, '张三的测试图书描述9，张三的测试图书描述9，张三的测试图书描述1', 'https://placekitten.com/400/600?image=9', FROM_UNIXTIME( 1614136703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书10', 7, 3, '张三的测试图书描述10，张三的测试图书描述10，张三的测试图书描述1', 'https://placekitten.com/400/600?image=10', FROM_UNIXTIME( 1614137703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书11', 4, 10, '张三的测试图书描述11，张三的测试图书描述11，张三的测试图书描述1', 'https://placekitten.com/400/600?image=11', FROM_UNIXTIME( 1614138703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书12', 5, 1, '张三的测试图书描述12，张三的测试图书描述12，张三的测试图书描述1', 'https://placekitten.com/400/600?image=12', FROM_UNIXTIME( 1614139703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书13', 10, 4, '张三的测试图书描述13，张三的测试图书描述13，张三的测试图书描述1', 'https://placekitten.com/400/600?image=13', FROM_UNIXTIME( 1614140703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书14', 10, 6, '张三的测试图书描述14，张三的测试图书描述14，张三的测试图书描述1', 'https://placekitten.com/400/600?image=14', FROM_UNIXTIME( 1614141703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书15', 10, 5, '张三的测试图书描述15，张三的测试图书描述15，张三的测试图书描述1', 'https://placekitten.com/400/600?image=15', FROM_UNIXTIME( 1614142703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书16', 10, 7, '张三的测试图书描述16，张三的测试图书描述16，张三的测试图书描述1', 'https://placekitten.com/400/600?image=16', FROM_UNIXTIME( 1614143703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书17', 1, 7, '张三的测试图书描述17，张三的测试图书描述17，张三的测试图书描述1', 'https://placekitten.com/400/600?image=17', FROM_UNIXTIME( 1614144703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书18', 6, 9, '张三的测试图书描述18，张三的测试图书描述18，张三的测试图书描述1', 'https://placekitten.com/400/600?image=18', FROM_UNIXTIME( 1614145703, '%Y-%m-%d %H:%i:%s' ) ),
	( '张三的测试图书19', 8, 8, '张三的测试图书描述19，张三的测试图书描述19，张三的测试图书描述1', 'https://placekitten.com/400/600?image=19', FROM_UNIXTIME( 1614146703, '%Y-%m-%d %H:%i:%s' ) );

INSERT INTO fm_words ( word_english, word_translate, word_baidescription, word_cover_url, word_category )
VALUES
	( 'withouot have to...', '无需...', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=22', 1 ),
	( 'work', '工作', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=0', 2 ),
	( 'wrap...around...', '卷绕的，环绕的', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=1', 4 ),
	( 'wrap...with...', '用……包', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=2', 2 ),
	( 'wrapper', '包装', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=3', 3 ),
	( 'write out', '写出', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=4', 1 ),
	( 'yield', '出产', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=5', 4 ),
	( 'zone', '地区', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=6', 3 ),
	( 'view', '视图', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=7', 2 ),
	( 'violate', '违反', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=8', 4 ),
	( 'virtual', '虚拟', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=9', 2 ),
	( 'visit', '访问', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=10', 1 ),
	( 'whichever', '任何一个', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=11', 1 ),
	( 'whitespace', '空白', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=12', 1 ),
	( 'widening', '拉大', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=13', 5 ),
	( 'width', '宽度', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=14', 1 ),
	( 'without', '没有', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=15', 2 ),
	( 'similarly', '同样地', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=16', 5 ),
	( 'simple', '笨蛋', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=17', 2 ),
	( 'sink', '水槽', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=18', 3 ),
	( 'size', '大小', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=19', 1 ),
	( 'skip', '跳跃', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=20', 2 ),
	( 'sleep', '睡觉', '作者很懒，该单词没有描述', 'https://placekitten.com/380/200?image=21', 4 );

INSERT INTO fm_lexicons ( lexicon_name, lexicon_author, lexicon_baidescription, lexicon_create_date, lexicon_cover_url )
VALUES
	( '张三的测试词库', 1, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 1614127703, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=21' ),
	( '张三的测试词库', 2, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161402000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=0' ),
	( '张三的测试词库', 4, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161403000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=1' ),
	( '张三的测试词库', 2, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161404000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=2' ),
	( '张三的测试词库', 6, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161405000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=3' ),
	( '张三的测试词库', 8, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161406000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=4' ),
	( '张三的测试词库', 6, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161407000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=5' ),
	( '张三的测试词库', 5, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161408000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=6' ),
	( '张三的测试词库', 5, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161409000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=7' ),
	( '张三的测试词库', 4, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161410000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=8' ),
	( '张三的测试词库', 8, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161411000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=9' ),
	( '张三的测试词库', 9, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161412000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=10' ),
	( '张三的测试词库', 4, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161413000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=11' ),
	( '张三的测试词库', 9, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161414000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=12' ),
	( '张三的测试词库', 8, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161415000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=13' ),
	( '张三的测试词库', 2, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161416000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=14' ),
	( '张三的测试词库', 6, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161417000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=15' ),
	( '张三的测试词库', 1, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161418000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=16' ),
	( '张三的测试词库', 1, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161419000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=17' ),
	( '张三的测试词库', 4, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161420000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=18' ),
	( '张三的测试词库', 3, '张三的测试词库描述，张三的测试词库描述，张三的测试词库描述', FROM_UNIXTIME( 161421000, '%Y-%m-%d %H:%i:%s' ), 'https://placekitten.com/400/400?image=19' );

INSERT INTO fm_notes ( word_id, note_author, note_baidescription, note_create_date )
VALUES
	(
		1,
		1,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161411000, '%Y-%m-%d %H:%i:%s' )),
	(
		10,
		6,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161411000, '%Y-%m-%d %H:%i:%s' )),
	(
		16,
		4,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161412000, '%Y-%m-%d %H:%i:%s' )),
	(
		1,
		9,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161413000, '%Y-%m-%d %H:%i:%s' )),
	(
		12,
		4,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161414000, '%Y-%m-%d %H:%i:%s' )),
	(
		12,
		4,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161415000, '%Y-%m-%d %H:%i:%s' )),
	(
		14,
		1,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161416000, '%Y-%m-%d %H:%i:%s' )),
	(
		17,
		3,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161417000, '%Y-%m-%d %H:%i:%s' )),
	(
		19,
		10,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161418000, '%Y-%m-%d %H:%i:%s' )),
	(
		4,
		2,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161419000, '%Y-%m-%d %H:%i:%s' )),
	(
		15,
		9,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161420000, '%Y-%m-%d %H:%i:%s' )),
	(
		11,
		1,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161421000, '%Y-%m-%d %H:%i:%s' )),
	(
		2,
		6,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161422000, '%Y-%m-%d %H:%i:%s' )),
	(
		4,
		2,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161423000, '%Y-%m-%d %H:%i:%s' )),
	(
		11,
		2,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161424000, '%Y-%m-%d %H:%i:%s' )),
	(
		16,
		9,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161425000, '%Y-%m-%d %H:%i:%s' )),
	(
		6,
		6,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161426000, '%Y-%m-%d %H:%i:%s' )),
	(
		6,
		8,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161427000, '%Y-%m-%d %H:%i:%s' )),
	(
		15,
		8,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161428000, '%Y-%m-%d %H:%i:%s' )),
	(
		21,
		1,
		'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161429000, '%Y-%m-%d %H:%i:%s' )),
	(
		20,
		10,
	'这个单词的笔记，我说我是乱写的',
	FROM_UNIXTIME( 161430000, '%Y-%m-%d %H:%i:%s' ));

INSERT INTO fm_lexicon_word_mapping ( lexicon_id, word_id )
VALUES
	( 1, 1 ),
	( 1, 2 ),
	( 1, 3 ),
	( 1, 4 ),
	( 1, 5 ),
	( 1, 6 ),
	( 1, 7 ),
	( 1, 8 ),
	( 1, 9 ),
	( 1, 10 ),
	( 2, 7 ),
	( 2, 8 ),
	( 2, 9 ),
	( 2, 10 ),
	( 2, 11 ),
	( 2, 12 ),
	( 2, 13 ),
	( 2, 14 ),
	( 2, 15 ),
	( 3, 2 ),
	( 3, 3 ),
	( 3, 4 ),
	( 3, 5 ),
	( 3, 6 ),
	( 3, 7 ),
	( 3, 8 ),
	( 3, 9 ),
	( 3, 10 ),
	( 4, 7 ),
	( 4, 8 ),
	( 4, 9 ),
	( 4, 10 ),
	( 4, 11 ),
	( 4, 12 ),
	( 4, 13 ),
	( 4, 14 ),
	( 4, 15 ),
	( 5, 3 ),
	( 5, 4 ),
	( 5, 5 ),
	( 5, 6 ),
	( 5, 7 ),
	( 5, 8 ),
	( 5, 9 ),
	( 5, 10 ),
	( 5, 11 );

INSERT INTO fm_user_book_mapping ( user_id, book_id )
VALUES
	( 1, 1 ),
	( 1, 2 ),
	( 1, 3 ),
	( 1, 4 ),
	( 1, 5 ),
	( 1, 6 ),
	( 1, 7 ),
	( 1, 8 ),
	( 1, 9 ),
	( 1, 10 ),
	( 2, 9 ),
	( 2, 10 ),
	( 2, 11 ),
	( 2, 12 ),
	( 2, 13 ),
	( 2, 14 ),
	( 2, 15 ),
	( 2, 16 ),
	( 2, 17 ),
	( 3, 7 ),
	( 3, 8 ),
	( 3, 9 ),
	( 3, 10 ),
	( 3, 11 ),
	( 3, 12 ),
	( 3, 13 ),
	( 3, 14 ),
	( 3, 15 ),
	( 4, 6 ),
	( 4, 7 ),
	( 4, 8 ),
	( 4, 9 ),
	( 4, 10 ),
	( 4, 11 ),
	( 4, 12 ),
	( 4, 13 ),
	( 4, 14 ),
	( 5, 10 ),
	( 5, 11 ),
	( 5, 12 ),
	( 5, 13 ),
	( 5, 14 ),
	( 5, 15 ),
	( 5, 16 ),
	( 5, 17 ),
	( 5, 18 ),
	( 6, 7 ),
	( 6, 8 ),
	( 6, 9 ),
	( 6, 10 ),
	( 6, 11 ),
	( 6, 12 ),
	( 6, 13 ),
	( 6, 14 ),
	( 6, 15 ),
	( 7, 5 ),
	( 7, 6 ),
	( 7, 7 ),
	( 7, 8 ),
	( 7, 9 ),
	( 7, 10 ),
	( 7, 11 ),
	( 7, 12 ),
	( 7, 13 ),
	( 8, 4 ),
	( 8, 5 ),
	( 8, 6 ),
	( 8, 7 ),
	( 8, 8 ),
	( 8, 9 ),
	( 8, 10 ),
	( 8, 11 ),
	( 8, 12 ),
	( 9, 8 ),
	( 9, 9 ),
	( 9, 10 ),
	( 9, 11 ),
	( 9, 12 ),
	( 9, 13 ),
	( 9, 14 ),
	( 9, 15 ),
	( 9, 16 );

INSERT INTO fm_user_lexicons ( user_id, lexicon_id )
VALUES
	( 1, 1 ),
	( 1, 3 ),
	( 1, 4 ),
	( 1, 5 ),
	( 1, 6 ),
	( 1, 7 ),
	( 1, 8 ),
	( 1, 9 ),
	( 1, 10 ),
	( 1, 11 ),
	( 2, 8 ),
	( 2, 9 ),
	( 2, 10 ),
	( 2, 11 ),
	( 2, 12 ),
	( 2, 13 ),
	( 2, 14 ),
	( 2, 15 ),
	( 2, 16 ),
	( 3, 8 ),
	( 3, 9 ),
	( 3, 10 ),
	( 3, 11 ),
	( 3, 12 ),
	( 3, 13 ),
	( 3, 14 ),
	( 3, 15 ),
	( 3, 16 ),
	( 4, 4 ),
	( 4, 5 ),
	( 4, 6 ),
	( 4, 7 ),
	( 4, 8 ),
	( 4, 9 ),
	( 4, 10 ),
	( 4, 11 ),
	( 4, 12 ),
	( 5, 7 ),
	( 5, 8 ),
	( 5, 9 ),
	( 5, 10 ),
	( 5, 11 ),
	( 5, 12 ),
	( 5, 13 ),
	( 5, 14 ),
	( 5, 15 ),
	( 6, 8 ),
	( 6, 9 ),
	( 6, 10 ),
	( 6, 11 ),
	( 6, 12 ),
	( 6, 13 ),
	( 6, 14 ),
	( 6, 15 ),
	( 6, 16 ),
	( 7, 7 ),
	( 7, 8 ),
	( 7, 9 ),
	( 7, 10 ),
	( 7, 11 ),
	( 7, 12 ),
	( 7, 13 ),
	( 7, 14 ),
	( 7, 15 ),
	( 8, 11 ),
	( 8, 12 ),
	( 8, 13 ),
	( 8, 14 ),
	( 8, 15 ),
	( 8, 16 ),
	( 8, 17 ),
	( 8, 18 ),
	( 8, 19 ),
	( 9, 6 ),
	( 9, 7 ),
	( 9, 8 ),
	( 9, 9 ),
	( 9, 10 ),
	( 9, 11 ),
	( 9, 12 ),
	( 9, 13 ),
	( 9, 14 );