import { ToastType } from './types';

type ToastListener = (message: string, type: ToastType) => void;

class ToastBus {
  private listeners: ToastListener[] = [];

  subscribe(listener: ToastListener) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener);
    };
  }

  emit(message: string, type: ToastType) {
    this.listeners.forEach((l) => l(message, type));
  }
}

export const toastBus = new ToastBus();

export const toast = {
  success: (msg: string) => toastBus.emit(msg, 'success'),
  error: (msg: string) => toastBus.emit(msg, 'error'),
  info: (msg: string) => toastBus.emit(msg, 'info'),
  warning: (msg: string) => toastBus.emit(msg, 'warning'),
};
