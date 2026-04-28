import { create } from 'zustand';
import type { RetailTemplate, MenuTemplate, PromoTemplate, Product, UiConfig, NavigationState } from '@/types';

const DEFAULT_UI_CONFIG: UiConfig = {
  showSearchBar: false,
  showMicButton: false,
  retailColumns: 4,
};

interface AppState {
  retailTemplate: RetailTemplate;
  menuTemplate: MenuTemplate;
  promoTemplate: PromoTemplate;
  currentMode: string;
  searchQuery: string;
  selectedProduct: Product | null;
  uiConfig: UiConfig;
  navigation: NavigationState | null;
  searchRecommendation: string;
  searchResults: Product[];
  products: Product[];
  productsLoaded: boolean;
  
  setRetailTemplate: (template: RetailTemplate) => void;
  setMenuTemplate: (template: MenuTemplate) => void;
  setPromoTemplate: (template: PromoTemplate) => void;
  setCurrentMode: (mode: string) => void;
  setSearchQuery: (query: string) => void;
  setSelectedProduct: (product: Product | null) => void;
  setUiConfig: (config: Partial<UiConfig>) => void;
  setNavigation: (navigation: NavigationState | null) => void;
  startNavigation: (destination: string, estimatedTime?: number, distance?: number) => void;
  setSearchRecommendation: (recommendation: string) => void;
  setSearchResults: (results: Product[]) => void;
  setProducts: (products: Product[]) => void;
  setProductsLoaded: (loaded: boolean) => void;
}

export const useAppStore = create<AppState>((set) => ({
  retailTemplate: 'grid',
  menuTemplate: 'classic',
  promoTemplate: 'video',
  currentMode: 'home',
  searchQuery: '',
  selectedProduct: null,
  uiConfig: DEFAULT_UI_CONFIG,
  navigation: null,
  searchRecommendation: '',
  searchResults: [],
  products: [],
  productsLoaded: false,
  
  setRetailTemplate: (template) => set({ retailTemplate: template }),
  setMenuTemplate: (template) => set({ menuTemplate: template }),
  setPromoTemplate: (template) => set({ promoTemplate: template }),
  setCurrentMode: (mode) => set({ currentMode: mode }),
  setSearchQuery: (query) => set({ searchQuery: query }),
  setSelectedProduct: (product) => set({ selectedProduct: product }),
  setUiConfig: (config) => set((state) => ({ uiConfig: { ...state.uiConfig, ...config } })),
  setNavigation: (navigation) => set({ navigation }),
  startNavigation: (destination, estimatedTime, distance) => set({
    currentMode: 'navigating',
    navigation: {
      destination,
      isNavigating: true,
      estimatedTime,
      distance,
    },
  }),
  setSearchRecommendation: (recommendation) => set({ searchRecommendation: recommendation }),
  setSearchResults: (results) => set({ searchResults: results }),
  setProducts: (products) => set({ products, productsLoaded: true }),
  setProductsLoaded: (loaded) => set({ productsLoaded: loaded }),
}));
