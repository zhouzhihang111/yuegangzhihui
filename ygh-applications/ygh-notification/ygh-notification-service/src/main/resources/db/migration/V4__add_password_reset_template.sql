INSERT INTO notification_template(id,code,title_template,content_template)
VALUES(4,'AUTH_PASSWORD_RESET','密码重置申请','您的密码重置令牌为：{{resetToken}}。令牌将在 {{expiresMinutes}} 分钟后失效；若非本人操作，请忽略。');
