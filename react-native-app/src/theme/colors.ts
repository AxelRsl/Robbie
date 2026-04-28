// Paleta de colores basada en ROBBIE-PANEL
export const Colors = {
  // Colores principales (basados en ROBBIE-PANEL)
  primary: '#E4027C',        // Rosa Ikalp principal
  primaryDark: '#C2185B',    // Rosa más oscuro
  primaryLight: '#F472B6',   // Rosa claro para modo oscuro
  
  // Gradientes
  gradientStart: '#E91E63',  // from-pink-500
  gradientEnd: '#E53E3E',    // to-rose-600
  
  // Colores de superficie (glassmorphism)
  surface: '#FFFFFF',
  surfaceAlt: '#FFF8F9',
  surfaceDark: '#0F172A',
  surfaceAltDark: '#1E293B',
  
  // Colores de vidrio (glass effects)
  glassLight: 'rgba(255, 255, 255, 0.6)',
  glassDark: 'rgba(30, 41, 59, 0.6)',
  glassBorderLight: 'rgba(233, 30, 99, 0.15)',
  glassBorderDark: 'rgba(148, 163, 184, 0.12)',
  
  // Texto
  textPrimary: '#28171A',
  textSecondary: '#5B3F43',
  textPrimaryDark: '#F1F5F9',
  textSecondaryDark: '#94A3B8',
  
  // Estados
  success: '#10B981',
  warning: '#F59E0B',
  error: '#EF4444',
  info: '#3B82F6',
  
  // Colores de acento
  accent: '#00BFA5',         // Teal de Ikalp
  accentDark: '#00A693',
  
  // Backgrounds especiales
  dotPattern: '#EECBCC',
  dotPatternDark: 'rgba(148, 163, 184, 0.08)',
  
  // Transparencias para overlays
  overlay: 'rgba(0, 0, 0, 0.5)',
  overlayLight: 'rgba(255, 255, 255, 0.8)',
};

// Tema claro (basado en ROBBIE-PANEL light mode)
export const LightTheme = {
  colors: {
    primary: Colors.primary,
    primaryVariant: Colors.primaryDark,
    secondary: Colors.accent,
    secondaryVariant: Colors.accentDark,
    
    background: Colors.surface,
    surface: Colors.glassLight,
    surfaceVariant: Colors.surfaceAlt,
    
    onPrimary: '#FFFFFF',
    onSecondary: '#FFFFFF',
    onBackground: Colors.textPrimary,
    onSurface: Colors.textPrimary,
    onSurfaceVariant: Colors.textSecondary,
    
    // Glassmorphism específico
    glass: Colors.glassLight,
    glassBorder: Colors.glassBorderLight,
    
    // Estados
    success: Colors.success,
    warning: Colors.warning,
    error: Colors.error,
    info: Colors.info,
    
    // Dot pattern
    dotPattern: Colors.dotPattern,
  },
  isDark: false,
};

// Tema oscuro (basado en ROBBIE-PANEL dark mode)
export const DarkTheme = {
  colors: {
    primary: Colors.primaryLight,
    primaryVariant: Colors.primary,
    secondary: Colors.accent,
    secondaryVariant: Colors.accentDark,
    
    background: Colors.surfaceDark,
    surface: Colors.glassDark,
    surfaceVariant: Colors.surfaceAltDark,
    
    onPrimary: '#FFFFFF',
    onSecondary: '#FFFFFF',
    onBackground: Colors.textPrimaryDark,
    onSurface: Colors.textPrimaryDark,
    onSurfaceVariant: Colors.textSecondaryDark,
    
    // Glassmorphism específico
    glass: Colors.glassDark,
    glassBorder: Colors.glassBorderDark,
    
    // Estados
    success: '#34D399',
    warning: '#FBBF24',
    error: '#F87171',
    info: '#60A5FA',
    
    // Dot pattern
    dotPattern: Colors.dotPatternDark,
  },
  isDark: true,
};

export type Theme = typeof LightTheme;
