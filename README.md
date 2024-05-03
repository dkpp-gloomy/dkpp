# dkpp
dkpp

本项目使用nacos源代码启动nacos
使用本地mysql库进行启动
nacos相关配置文件 dkpp-nacos/console/启动类 加上如下文件

```properties
-Dnacos.standalone=true -Dspring.datasource.platform=mysql
```
使用127.0.0.1:3306启动mysql 并新增nacos用户密码为nacos
新增相关表结构dkpp-nacos/config/resources/META_INF/nacos-db.sql
idea安装Protobuf插件
并将dkpp/dkpp-nacos项目install
再进行启动Nacos启动类