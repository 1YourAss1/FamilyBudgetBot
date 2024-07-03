package ru.familybudget;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Main {
    static FamilyBudgetBot familyBudgetBot;

    public static void main(String[] args) throws TelegramApiException, IOException, SchedulerException {
        var app = new FileInputStream("app.properties");
        var properties = new Properties();
        properties.load(app);

        familyBudgetBot = new FamilyBudgetBot(
                properties.getProperty("bot.token"),
                properties.getProperty("bot.username"),
                properties.getProperty("bot.user_id_array"));

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(familyBudgetBot);

        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();

        JobBuilder dailyJobBuilder = JobBuilder.newJob(DailyReminder.class);
        Trigger dailyMorningTrigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule(properties.getProperty("cron.reminder.daily.morning")))
                .build();
        Trigger dailyEveningTrigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule(properties.getProperty("cron.reminder.daily.evening")))
                .build();

        scheduler.scheduleJob(dailyJobBuilder.withIdentity("morning").build(), dailyMorningTrigger);
        scheduler.scheduleJob(dailyJobBuilder.withIdentity("evening").build(), dailyEveningTrigger);

        JobDetail monthlyJob = JobBuilder.newJob(MonthlyStatistic.class).build();
        Trigger monthlyTrigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule(properties.getProperty("cron.reminder.monthly")))
                .build();

        scheduler.scheduleJob(monthlyJob, monthlyTrigger);
    }


    public static class DailyReminder implements Job {
        @Override
        public void execute(JobExecutionContext jobExecutionContext) {
            familyBudgetBot.sendDailyReminder();
        }
    }

    public static class MonthlyStatistic implements Job {
        @Override
        public void execute(JobExecutionContext jobExecutionContext) {
            familyBudgetBot.sendMonthStatisticToEveryone();
        }
    }
}
