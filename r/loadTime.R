dat1 <- read.csv("load_time_extended.csv", header = TRUE)
dat2 <- read.csv("load_time_seed.csv", header = TRUE)

mean(dat1$load_time)
sd(dat1$load_time)

mean(dat2$load_time)
sd(dat2$load_time)

library('ggplot2')

extended <- data.frame(group="extended", value=dat1$load_time)
original <- data.frame(group="original", value=dat2$load_time)
both <- rbind(extended, original)
plot <- ggplot(both, aes(x=group, y=value, fill=group)) +
  geom_boxplot() +
  labs(
    fill = "Seed", 
    title = "Load Time per Seed",
    y = "Milliseconds"
  ) +
  theme(
    axis.title.x = element_blank(),
    plot.title = element_text(hjust = 0.5)
  )
show(plot)

