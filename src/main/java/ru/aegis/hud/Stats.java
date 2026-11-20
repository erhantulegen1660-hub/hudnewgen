package ru.aegis.hud;

/** Данные игрока. seconds = наигранное время в секундах. */
public final class Stats {
    public String name = "";
    public long seconds;
    public int kills;
    public int wins;
    public int losses;
    public long amethysts;
    /** Убийства в текущем матче (не сохраняются). */
    public int matchKills;
}
