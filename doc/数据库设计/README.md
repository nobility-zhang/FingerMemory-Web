# 数据库设计

> 详细设计会在这几张基础表上进行扩展

所有表名都以`fm`作为前缀，主要的基础表如下：

* [用户表](用户表.md)
* [单词表](单词表.md)
* [图书表](图书表.md)
* [词库表](词库表.md)

创建名为`fm`的数据库

```sql
CREATE DATABASE `fm` CHARACTER SET 'utf8';
```

ER图如下：

![ER图](README/ER图.svg)

