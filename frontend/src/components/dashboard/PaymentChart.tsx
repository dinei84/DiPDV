'use client';
import { useEffect, useRef } from 'react';
import {
  Chart,
  ArcElement,
  Tooltip,
  Legend,
  DoughnutController,
} from 'chart.js';

Chart.register(ArcElement, Tooltip, Legend, DoughnutController);

interface Props {
  data: { method: string; totalAmount: number }[];
}

const COLORS = ['#1E3A5F', '#2D6A9F', '#F4A300', '#27AE60', '#E74C3C'];

export default function PaymentChart({ data }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const chartRef = useRef<Chart | null>(null);

  useEffect(() => {
    if (!canvasRef.current) return;
    if (chartRef.current) chartRef.current.destroy();

    chartRef.current = new Chart(canvasRef.current, {
      type: 'doughnut',
      data: {
        labels: data.map((d) => d.method),
        datasets: [
          {
            data: data.map((d) => d.totalAmount),
            backgroundColor: COLORS.slice(0, data.length),
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        plugins: {
          legend: { position: 'right' },
          tooltip: {
            callbacks: {
              label: (ctx) => ` R$ ${(ctx.raw as number).toFixed(2)}`,
            },
          },
        },
      },
    });

    return () => chartRef.current?.destroy();
  }, [data]);

  return (
    <div className="flex justify-center" style={{ height: 200 }}>
      <canvas ref={canvasRef} />
    </div>
  );
}
