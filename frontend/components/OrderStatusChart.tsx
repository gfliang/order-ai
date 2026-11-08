"use client";

import { useEffect, useRef } from "react";
import * as echarts from "echarts/core";
import { PieChart } from "echarts/charts";
import { GraphicComponent, LegendComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import type { EChartsCoreOption } from "echarts/core";
import { STATUS_META } from "@/lib/orders";

echarts.use([PieChart, GraphicComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

interface Props {
  counts: Record<string, number>;
}

const NARROW_WIDTH = 520;

function buildOption(counts: Record<string, number>, narrow: boolean): EChartsCoreOption {
  const data = Object.entries(STATUS_META).map(([status, meta]) => ({
    name: meta.label,
    value: counts[status] ?? 0,
    itemStyle: { color: meta.hex },
  }));
  const total = data.reduce((sum, item) => sum + item.value, 0);

  return {
    tooltip: { trigger: "item", formatter: "{b}: {c} 笔（{d}%）" },
    legend: {
      orient: narrow ? "horizontal" : "vertical",
      ...(narrow
        ? { bottom: 0, left: "center" }
        : { right: 8, top: "center" }),
      icon: "circle",
      itemWidth: 10,
      itemHeight: 10,
      itemGap: narrow ? 16 : 10,
      textStyle: { color: "rgba(0,0,0,0.65)", fontSize: narrow ? 12 : 14 },
    },
    graphic: total === 0
      ? [{ type: "text", left: "center", top: "middle", style: { text: "暂无订单数据", fill: "#999", fontSize: 14 } }]
      : [],
    series: [
      {
        name: "订单状态",
        type: "pie",
        radius: narrow ? ["42%", "64%"] : ["45%", "70%"],
        center: narrow ? ["50%", "42%"] : ["40%", "50%"],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: "#fff", borderWidth: 2 },
        label: narrow
          ? { show: false }
          : { show: true, formatter: "{b}\n{c}" },
        labelLine: { length: 8, length2: 8 },
        data,
      },
    ],
  };
}

export default function OrderStatusChart({ counts }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<ReturnType<typeof echarts.init> | null>(null);
  const countsRef = useRef(counts);
  const narrowRef = useRef(false);

  countsRef.current = counts;

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }
    const container = containerRef.current;
    const chart = echarts.init(container);
    chartRef.current = chart;
    narrowRef.current = container.clientWidth < NARROW_WIDTH;

    const handleResize = () => {
      chart.resize();
      const narrow = container.clientWidth < NARROW_WIDTH;
      if (narrow !== narrowRef.current) {
        narrowRef.current = narrow;
        chart.setOption(buildOption(countsRef.current, narrow), true);
      }
    };
    window.addEventListener("resize", handleResize);
    return () => {
      window.removeEventListener("resize", handleResize);
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) {
      return;
    }
    chart.setOption(buildOption(counts, narrowRef.current), true);
  }, [counts]);

  return <div ref={containerRef} className="order-chart" />;
}
