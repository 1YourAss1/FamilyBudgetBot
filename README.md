1. Create file `app.properties`:
```
bot.username=<Bot Name>
bot.token=<Bot Token>
bot.user_id_array=<User IDs for access>

cron.reminder.daily.morning=0 0 10 * * ?
cron.reminder.daily.evening=0 0 23 * * ?
cron.reminder.monthly=0 30 23 L * ?
```
2. Create database `budget.db` using `createdb.sql`
