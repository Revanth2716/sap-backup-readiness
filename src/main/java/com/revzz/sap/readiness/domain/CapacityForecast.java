package com.revzz.sap.readiness.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * When the database runs out of space.
 *
 * <p>The forecast is deliberately simple - a linear trend over the observed
 * window - because a number that a team can argue about ("are we really
 * growing 4 GB a day?") is worth more than a clever model nobody trusts.</p>
 */
public class CapacityForecast {

    private String systemName;
    private int currentSizeGb;
    private int maxSizeGb;
    private double growthPerDayGb;
    private Integer daysToFull;
    private LocalDate projectedFillDate;
    private boolean atRisk;

    public int headroomGb() {
        return Math.max(0, maxSizeGb - currentSizeGb);
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public int getCurrentSizeGb() {
        return currentSizeGb;
    }

    public void setCurrentSizeGb(int currentSizeGb) {
        this.currentSizeGb = currentSizeGb;
    }

    public int getMaxSizeGb() {
        return maxSizeGb;
    }

    public void setMaxSizeGb(int maxSizeGb) {
        this.maxSizeGb = maxSizeGb;
    }

    public double getGrowthPerDayGb() {
        return growthPerDayGb;
    }

    public void setGrowthPerDayGb(double growthPerDayGb) {
        this.growthPerDayGb = growthPerDayGb;
    }

    public Integer getDaysToFull() {
        return daysToFull;
    }

    public void setDaysToFull(Integer daysToFull) {
        this.daysToFull = daysToFull;
    }

    public LocalDate getProjectedFillDate() {
        return projectedFillDate;
    }

    public void setProjectedFillDate(LocalDate projectedFillDate) {
        this.projectedFillDate = projectedFillDate;
    }

    public boolean isAtRisk() {
        return atRisk;
    }

    public void setAtRisk(boolean atRisk) {
        this.atRisk = atRisk;
    }
}
