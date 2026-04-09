import { create } from 'zustand';
import type { RetailTemplate, MenuTemplate, PromoTemplate, Product } from '@/types';

interface AppState {
  retailTemplate: RetailTemplate;
  menuTemplate: MenuTemplate;
  promoTemplate: PromoTemplate;
  currentMode: string;
  searchQuery: string;
  selectedProduct: Product | null;
  
  setRetailTemplate: (template: RetailTemplate) => void;
  setMenuTemplate: (template: MenuTemplate) => void;
  setPromoTemplate: (template: PromoTemplate) => void;
  setCurrentMode: (mode: string) => void;
  setSearchQuery: (query: string) => void;
  setSelectedProduct: (product: Product | null) => void;
}

export const useAppStore = create<AppState>((set) => ({
  retailTemplate: 'grid',
  menuTemplate: 'classic',
  promoTemplate: 'video',
  currentMode: 'menu',
  searchQuery: '',
  selectedProduct: null,
  
  setRetailTemplate: (template) => set({ retailTemplate: template }),
  setMenuTemplate: (template) => set({ menuTemplate: template }),
  setPromoTemplate: (template) => set({ promoTemplate: template }),
  setCurrentMode: (mode) => set({ currentMode: mode }),
  setSearchQuery: (query) => set({ searchQuery: query }),
  setSelectedProduct: (product) => set({ selectedProduct: product }),
}));
