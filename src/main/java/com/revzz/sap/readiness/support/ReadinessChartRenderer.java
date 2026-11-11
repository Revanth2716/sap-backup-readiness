package com.revzz.sap.readiness.support;

import com.revzz.sap.readiness.domain.CapacityForecast;
import com.revzz.sap.readiness.domain.CapacitySample;
import com.revzz.sap.readiness.domain.Severity;
import com.revzz.sap.readiness.domain.SystemProfile;
import com.revzz.sap.readiness.domain.SystemReadiness;

import java.util.List;

/**
 * Server-side SVG charts.
 *
 * <p>The charts are drawn on the server, as plain SVG markup, with no chart
 * library, no CDN and no client-side JavaScript. That is a deliberate choice
 * for a tool that has to run inside a corporate landscape where outbound
 * internet access is often blocked, and where an auditor has to be able to
 * save the page and still see the evidence.</p>
 */
public final class ReadinessChartRenderer {

    private ReadinessChartRenderer() {
    }

    /** The colour of a severity band. */
    public static String severityColour(Severity severity) {
        if (severity == null) {
            return "#63748a";
        }
        switch (severity) {
            case CRITICAL:
                return "#d7263d";
            case HIGH:
                return "#e8710a";
            case MEDIUM:
                return "#b8860b";
            case LOW:
                return "#1a7f5a";
            default:
                return "#2e9e6b";
        }
    }

    /**
     * A horizontal bar chart of the readiness score of every system.
     *
     * @param readiness the systems, already sorted worst first
     */
    public static String readinessBars(List<SystemReadiness> readiness) {
        int width = 900;
        int labelWidth = 96;
        int valueWidth = 62;
        int rowHeight = 34;
        int top = 46;
        int height = top + Math.max(1, readiness.size()) * rowHeight + 16;

        int barWidth = width - labelWidth - valueWidth - 24;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"readiness\" viewBox=\"0 0 ").append(width).append(' ')
                .append(height).append("\" xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" ")
                .append("aria-label=\"Readiness score by system\">");

        svg.append("<text x=\"0\" y=\"18\" class=\"c-head\">READINESS SCORE BY SYSTEM</text>");
        svg.append("<text x=\"").append(width).append("\" y=\"18\" class=\"c-scale\" ")
                .append("text-anchor=\"end\">0 &#8211; 100</text>");

        // the track behind every bar
        svg.append("<rect x=\"").append(labelWidth).append("\" y=\"")
                .append(top - 14).append("\" width=\"").append(barWidth)
                .append("\" height=\"").append(Math.max(1, readiness.size()) * rowHeight)
                .append("\" fill=\"#eef2f7\" rx=\"4\"/>");

        for (int i = 0; i < readiness.size(); i++) {
            SystemReadiness item = readiness.get(i);
            int y = top + i * rowHeight;
            int filled = Math.round(barWidth * Math.max(0, Math.min(100, item.getScore())) / 100f);
            String colour = severityColour(item.getSeverity());

            svg.append("<text x=\"0\" y=\"").append(y + 5)
                    .append("\" class=\"c-label\">").append(esc(item.getProfile().getName()))
                    .append("</text>");
            svg.append("<rect x=\"").append(labelWidth).append("\" y=\"").append(y - 11)
                    .append("\" width=\"").append(Math.max(2, filled))
                    .append("\" height=\"20\" fill=\"").append(colour).append("\" rx=\"3\"/>");
            svg.append("<text x=\"").append(labelWidth + barWidth + 8).append("\" y=\"").append(y + 4)
                    .append("\" class=\"c-value\" fill=\"").append(colour).append("\">")
                    .append(item.getScore()).append("</text>");
            svg.append("<text x=\"").append(labelWidth + barWidth + 40).append("\" y=\"").append(y + 4)
                    .append("\" class=\"c-sub\">").append(item.getSeverity().name())
                    .append("</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    /**
     * The capacity trend of one system, with the limit and the projection.
     *
     * @param samples  the observed daily sizes
     * @param profile  the system, for the maximum size
     * @param forecast the projection, may be null
     */
    public static String capacityChart(List<CapacitySample> samples, SystemProfile profile,
                                       CapacityForecast forecast) {
        int width = 860;
        int height = 280;
        int left = 58;
        int right = 26;
        int top = 40;
        int bottom = 46;
        int plotWidth = width - left - right;
        int plotHeight = height - top - bottom;

        int maxSize = profile.getMaxDbGb();
        for (CapacitySample sample : samples) {
            maxSize = Math.max(maxSize, sample.getDbSizeGb());
        }
        maxSize = (int) Math.ceil(maxSize * 1.08);
        if (maxSize <= 0) {
            maxSize = 100;
        }

        int count = samples.size();
        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"capacity\" viewBox=\"0 0 ").append(width).append(' ')
                .append(height).append("\" xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" ")
                .append("aria-label=\"Database size trend for ").append(esc(profile.getName()))
                .append("\">");

        svg.append("<text x=\"0\" y=\"18\" class=\"c-head\">DATABASE SIZE - ")
                .append(esc(profile.getName())).append("</text>");
        svg.append("<text x=\"").append(width).append("\" y=\"18\" class=\"c-scale\" ")
                .append("text-anchor=\"end\">GB</text>");

        // horizontal grid lines with their values
        for (int i = 0; i <= 4; i++) {
            int value = (int) Math.round(maxSize * i / 4.0);
            int y = top + plotHeight - (int) Math.round(plotHeight * i / 4.0);
            svg.append("<line x1=\"").append(left).append("\" y1=\"").append(y)
                    .append("\" x2=\"").append(left + plotWidth).append("\" y2=\"").append(y)
                    .append("\" class=\"c-grid\"/>");
            svg.append("<text x=\"").append(left - 8).append("\" y=\"").append(y + 4)
                    .append("\" class=\"c-axis\" text-anchor=\"end\">").append(value).append("</text>");
        }

        // the maximum size - the line that must not be crossed
        int limitY = top + plotHeight
                - (int) Math.round(plotHeight * (double) profile.getMaxDbGb() / maxSize);
        svg.append("<line x1=\"").append(left).append("\" y1=\"").append(limitY)
                .append("\" x2=\"").append(left + plotWidth).append("\" y2=\"").append(limitY)
                .append("\" class=\"c-limit\"/>");
        svg.append("<text x=\"").append(left + 6).append("\" y=\"").append(limitY - 6)
                .append("\" class=\"c-limit-label\">maximum ").append(profile.getMaxDbGb())
                .append(" GB</text>");

        if (count >= 2) {
            StringBuilder points = new StringBuilder();
            for (int i = 0; i < count; i++) {
                CapacitySample sample = samples.get(i);
                int x = left + (int) Math.round(plotWidth * (double) i / (count - 1));
                int y = top + plotHeight
                        - (int) Math.round(plotHeight * (double) sample.getDbSizeGb() / maxSize);
                if (i > 0) {
                    points.append(' ');
                }
                points.append(x).append(',').append(y);
            }
            svg.append("<polyline points=\"").append(points).append("\" class=\"c-line\"/>");

            // the projection from today to the day the database fills up
            if (forecast != null && forecast.getDaysToFull() != null
                    && forecast.getDaysToFull() > 0 && forecast.getGrowthPerDayGb() > 0) {
                CapacitySample last = samples.get(count - 1);
                int x1 = left + plotWidth;
                int y1 = top + plotHeight
                        - (int) Math.round(plotHeight * (double) last.getDbSizeGb() / maxSize);
                int futureSize = last.getDbSizeGb()
                        + (int) Math.round(forecast.getGrowthPerDayGb() * forecast.getDaysToFull());
                int x2 = left + plotWidth + (int) Math.round(plotWidth
                        * Math.min(1.0, forecast.getDaysToFull() / 30.0));
                if (x2 > left + plotWidth) {
                    x2 = left + plotWidth;
                }
                int y2 = top + plotHeight
                        - (int) Math.round(plotHeight * (double) futureSize / maxSize);
                svg.append("<line x1=\"").append(x1).append("\" y1=\"").append(y1)
                        .append("\" x2=\"").append(x2).append("\" y2=\"").append(y2)
                        .append("\" class=\"c-projection\"/>");
                svg.append("<circle cx=\"").append(x2).append("\" cy=\"").append(y2)
                        .append("\" r=\"4\" class=\"c-projection-dot\"/>");
                svg.append("<text x=\"").append(x2 - 4).append("\" y=\"").append(y2 - 10)
                        .append("\" class=\"c-projection-label\" text-anchor=\"end\">full in ")
                        .append(forecast.getDaysToFull()).append(" days</text>");
            }

            // the first and the last date
            CapacitySample first = samples.get(0);
            CapacitySample last = samples.get(count - 1);
            svg.append("<text x=\"").append(left).append("\" y=\"").append(top + plotHeight + 20)
                    .append("\" class=\"c-axis\">").append(first.getSampleDate().toString())
                    .append("</text>");
            svg.append("<text x=\"").append(left + plotWidth).append("\" y=\"")
                    .append(top + plotHeight + 20).append("\" class=\"c-axis\" text-anchor=\"end\">")
                    .append(last.getSampleDate().toString()).append("</text>");
            svg.append("<text x=\"").append(left + plotWidth / 2).append("\" y=\"")
                    .append(top + plotHeight + 36)
                    .append("\" class=\"c-axis\" text-anchor=\"middle\">")
                    .append(count).append(" samples</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    /** Escapes the five characters that matter in XML text and attributes. */
    public static String esc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&apos;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
