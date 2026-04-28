import { StyleSheet, Dimensions } from 'react-native';
import { Colors, LightTheme, DarkTheme, Theme } from './colors';

const { width, height } = Dimensions.get('window');

// Función para crear estilos basados en el tema actual
export const createStyles = (theme: Theme) => StyleSheet.create({
  // Contenedores principales (basados en ROBBIE-PANEL)
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  
  // Glass panel effect (similar al panel web)
  glassPanel: {
    backgroundColor: theme.colors.glass,
    borderWidth: 1,
    borderColor: theme.colors.glassBorder,
    borderRadius: 12,
    // Note: React Native no soporta backdrop-filter, usaremos BlurView component
  },
  
  // Glass card con hover effect (adaptado para touch)
  glassCard: {
    backgroundColor: theme.colors.glass,
    borderWidth: 1,
    borderColor: theme.colors.glassBorder,
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    // Sombra para simular el efecto glass
    shadowColor: theme.isDark ? '#000' : theme.colors.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: theme.isDark ? 0.3 : 0.1,
    shadowRadius: 8,
    elevation: 4,
  },
  
  // Header estilo panel
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: theme.colors.glass,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.glassBorder,
  },
  
  // Sidebar style (para drawer navigation)
  sidebar: {
    backgroundColor: theme.colors.glass,
    borderRightWidth: 1,
    borderRightColor: theme.colors.glassBorder,
  },
  
  // Botones estilo panel
  primaryButton: {
    backgroundColor: theme.colors.primary,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: theme.colors.primary,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 4,
    elevation: 3,
  },
  
  primaryButtonText: {
    color: theme.colors.onPrimary,
    fontSize: 16,
    fontWeight: '600',
  },
  
  secondaryButton: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: theme.colors.primary,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  
  secondaryButtonText: {
    color: theme.colors.primary,
    fontSize: 16,
    fontWeight: '600',
  },
  
  // Texto estilo panel
  heading: {
    fontSize: 24,
    fontWeight: '700',
    color: theme.colors.onBackground,
    letterSpacing: -0.5,
    marginBottom: 8,
  },
  
  subheading: {
    fontSize: 18,
    fontWeight: '600',
    color: theme.colors.onBackground,
    marginBottom: 6,
  },
  
  body: {
    fontSize: 16,
    color: theme.colors.onSurface,
    lineHeight: 24,
  },
  
  caption: {
    fontSize: 14,
    color: theme.colors.onSurfaceVariant,
    lineHeight: 20,
  },
  
  // Input fields estilo panel
  inputField: {
    borderWidth: 1,
    borderColor: theme.colors.glassBorder,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    color: theme.colors.onSurface,
    backgroundColor: theme.colors.glass,
  },
  
  inputFieldFocused: {
    borderColor: theme.colors.primary,
    shadowColor: theme.colors.primary,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 4,
    elevation: 2,
  },
  
  // Cards de menú (estilo moderno del panel)
  menuCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.colors.glass,
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: theme.colors.glassBorder,
    shadowColor: theme.isDark ? '#000' : theme.colors.primary,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: theme.isDark ? 0.3 : 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  
  menuIconContainer: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  
  menuContent: {
    flex: 1,
  },
  
  menuTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: theme.colors.onSurface,
    marginBottom: 4,
  },
  
  menuDescription: {
    fontSize: 14,
    color: theme.colors.onSurfaceVariant,
  },
  
  menuArrow: {
    fontSize: 20,
    color: theme.colors.onSurfaceVariant,
  },
  
  // Gradientes (usando LinearGradient component)
  gradientContainer: {
    borderRadius: 12,
    overflow: 'hidden',
  },
  
  // Estados de carga
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: theme.colors.background,
  },
  
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: theme.colors.onSurfaceVariant,
  },
  
  // Efectos especiales
  glowEffect: {
    shadowColor: theme.colors.primary,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.5,
    shadowRadius: 10,
    elevation: 8,
  },
  
  // Layout responsive
  responsiveContainer: {
    paddingHorizontal: width > 768 ? 32 : 16,
    paddingVertical: 16,
  },
  
  // Dot pattern background (simulado con View)
  dotPatternBackground: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: theme.colors.background,
    opacity: 0.1,
  },
});

// Estilos globales que no dependen del tema
export const GlobalStyles = StyleSheet.create({
  // Utilidades de layout
  row: {
    flexDirection: 'row',
  },
  
  column: {
    flexDirection: 'column',
  },
  
  center: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  
  spaceBetween: {
    justifyContent: 'space-between',
  },
  
  spaceAround: {
    justifyContent: 'space-around',
  },
  
  flex1: {
    flex: 1,
  },
  
  // Espaciado
  p4: { padding: 4 },
  p8: { padding: 8 },
  p12: { padding: 12 },
  p16: { padding: 16 },
  p20: { padding: 20 },
  p24: { padding: 24 },
  
  m4: { margin: 4 },
  m8: { margin: 8 },
  m12: { margin: 12 },
  m16: { margin: 16 },
  m20: { margin: 20 },
  m24: { margin: 24 },
  
  // Bordes redondeados
  rounded4: { borderRadius: 4 },
  rounded8: { borderRadius: 8 },
  rounded12: { borderRadius: 12 },
  rounded16: { borderRadius: 16 },
  roundedFull: { borderRadius: 9999 },
});

// Animaciones y transiciones
export const Animations = {
  fadeIn: {
    from: { opacity: 0 },
    to: { opacity: 1 },
  },
  
  slideInFromRight: {
    from: { transform: [{ translateX: width }] },
    to: { transform: [{ translateX: 0 }] },
  },
  
  slideInFromLeft: {
    from: { transform: [{ translateX: -width }] },
    to: { transform: [{ translateX: 0 }] },
  },
  
  scaleIn: {
    from: { transform: [{ scale: 0.8 }], opacity: 0 },
    to: { transform: [{ scale: 1 }], opacity: 1 },
  },
};
