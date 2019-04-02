dat1 <- read.csv("wait_time_extended.csv", header = TRUE)
dat2 <- read.csv("wait_time_seed.csv", header = TRUE)

mean(dat1$wait_time)
sd(dat1$wait_time)

mean(dat2$wait_time)
sd(dat2$wait_time)

library('ggplot2')

extended <- data.frame(group="extended", value=dat1$wait_time)
original <- data.frame(group="original", value=dat2$wait_time)
both <- rbind(extended, original)
plot <- ggplot(both, aes(x=group, y=value, fill=group)) +
  geom_boxplot() +
  labs(
    fill = "Seed", 
    title = "Queue Wait Time per Seed",
    y = "Minutes"
  ) +
  theme(
    axis.title.x = element_blank(),
    plot.title = element_text(hjust = 0.5)
  )
show(plot)

