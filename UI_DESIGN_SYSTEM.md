# Hulunote UI Design System

This document defines the visual design language for Hulunote. All pages and components should follow these guidelines to maintain consistency across the application.

## 🎨 Color Palette

### Primary Colors (Gradient)
```
Primary Gradient: linear-gradient(135deg, #667eea 0%, #764ba2 100%)
- Start: #667eea (Soft Purple)
- End: #764ba2 (Deep Purple)
```

### Neutral Colors
| Name | Hex | Usage |
|------|-----|-------|
| Dark Navy | `#1a1a2e` | Headers, titles, footer background |
| Dark Gray | `#2d2d44` | Secondary dark backgrounds |
| Text Primary | `#1a1a2e` | Main text |
| Text Secondary | `#666666` | Descriptions, labels |
| Text Muted | `#999999` | Placeholder, hints |
| Light Purple | `#a5b4fc` | Links on dark backgrounds |
| Light Background | `#f8f9fa` | Page backgrounds |
| White | `#ffffff` | Cards, inputs |
| Border | `#e0e0e0` | Input borders, dividers |

### Semantic Colors
| Name | Hex | Usage |
|------|-----|-------|
| Success Green | `#98c379` | Code highlights, success states |
| Accent Purple Light | `rgba(102, 126, 234, 0.3)` | Tags, badges on dark bg |

## 📐 Typography

### Font Family
```css
font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
```

### Font Sizes
| Element | Size | Weight |
|---------|------|--------|
| Hero Title | 52px | 800 |
| Section Title | 36px | 700 |
| Card Title | 20px | 600 |
| Subsection Title | 18px | 600 |
| Body Text | 16px | 400 |
| Small Text | 14px | 400/500 |
| Code | 14px | Monaco, Consolas, monospace |

### Line Heights
- Headings: 1.2
- Body text: 1.6
- Code blocks: 2

## 📦 Components

### Buttons

#### Primary Button
```clojure
{:style {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
         :color "#fff"
         :border "none"
         :padding "14px 32px"
         :border-radius "30px"
         :font-size "16px"
         :font-weight "600"
         :cursor "pointer"
         :box-shadow "0 4px 15px rgba(102, 126, 234, 0.4)"}}
```

#### Secondary Button (Outline)
```clojure
{:style {:background "transparent"
         :color "#fff"  ; or #667eea on light bg
         :border "2px solid #fff"  ; or #667eea
         :padding "12px 28px"
         :border-radius "30px"
         :font-size "16px"
         :font-weight "600"}}
```

#### Small Button (Header)
```clojure
{:style {:background "#fff"
         :color "#667eea"
         :border "none"
         :padding "8px 20px"
         :border-radius "20px"
         :font-weight "600"}}
```

### Cards

#### Feature Card
```clojure
{:style {:background "#fff"
         :border-radius "12px"
         :padding "32px 24px"
         :box-shadow "0 4px 20px rgba(0,0,0,0.08)"
         :transition "transform 0.3s, box-shadow 0.3s"}}
```

#### Database Card
```clojure
{:style {:background "#fff"
         :border-radius "12px"
         :padding "32px 24px"
         :box-shadow "0 2px 12px rgba(0,0,0,0.08)"
         :border "2px solid transparent"}}
```

#### Form Card (Login/Signup)
```clojure
{:style {:background "#fff"
         :border-radius "16px"
         :padding "40px"
         :max-width "400px"
         :box-shadow "0 10px 40px rgba(0,0,0,0.1)"}}
```

### Input Fields
```clojure
{:style {:width "100%"
         :padding "12px 16px"
         :font-size "15px"
         :border "2px solid #e0e0e0"
         :border-radius "10px"
         :outline "none"
         :transition "border-color 0.3s"}}
; Focus state: border-color: #667eea
```

### Tags/Badges
```clojure
{:style {:background "rgba(102, 126, 234, 0.3)"
         :color "#a5b4fc"
         :padding "8px 16px"
         :border-radius "20px"
         :font-size "14px"}}
```

## 📏 Spacing

### Standard Spacing Scale
| Name | Value | Usage |
|------|-------|-------|
| xs | 8px | Tight spacing |
| sm | 12px | Small gaps |
| md | 16px | Default gaps |
| lg | 24px | Section gaps |
| xl | 32px | Card padding |
| 2xl | 40px | Major sections |
| 3xl | 48px | Section titles margin |
| 4xl | 80px | Section padding |

### Page Padding
- Section horizontal: 20px (mobile), 32px (desktop)
- Section vertical: 80px
- Card internal: 32px (feature), 40px (form)

## 🖼️ Layout

### Header
```clojure
{:style {:display "flex"
         :align-items "center"
         :justify-content "space-between"
         :padding "0 32px"
         :height "60px"
         :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"}}
```

### Footer
```clojure
{:style {:background "#1a1a2e"
         :padding "40px 20px"  ; or "24px 20px" for simple
         :text-align "center"}}
```

### Grid Layouts
```clojure
; Feature cards grid
{:style {:display "grid"
         :grid-template-columns "repeat(auto-fit, minmax(280px, 1fr))"
         :gap "24px"}}

; Database cards grid
{:style {:display "grid"
         :grid-template-columns "repeat(auto-fill, minmax(220px, 1fr))"
         :gap "20px"}}

; Tech stack grid
{:style {:display "grid"
         :grid-template-columns "repeat(auto-fit, minmax(300px, 1fr))"
         :gap "40px"}}
```

### Max Widths
| Content Type | Max Width |
|--------------|-----------|
| Wide content | 1200px |
| Standard content | 900px |
| Narrow content | 800px |
| Form cards | 400px |

## 🌓 Section Backgrounds

### Light Sections
```clojure
{:style {:background "#f8f9fa"
         :padding "80px 20px"}}
```

### White Sections
```clojure
{:style {:background "#fff"
         :padding "80px 20px"}}
```

### Dark Sections
```clojure
{:style {:background "linear-gradient(135deg, #1a1a2e 0%, #2d2d44 100%)"
         :padding "80px 20px"}}
```

### Gradient Sections (CTA, Hero)
```clojure
{:style {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
         :padding "80px 20px"}}
```

## 🎯 Icons & Emojis

Use emojis for visual appeal in feature cards and section headers:
- 📝 Notes/Writing
- 🔗 Links/Connections
- 📅 Calendar/Daily
- 📚 Database/Collection
- 🤖 AI/Automation
- 🔌 Integration
- ✨ Features/Magic
- 🧠 Knowledge/Intelligence
- 💬 Chat/Communication
- 🦀 Backend/Rust
- ⚛️ Frontend/React-style
- 👋 Welcome
- ⭐ Star/Favorite

## 📱 Responsive Design

### Breakpoints
- Mobile: < 768px
- Tablet: 768px - 1024px
- Desktop: > 1024px

### Mobile Adjustments
- Reduce section padding to 40px vertical
- Stack flex layouts vertically
- Reduce font sizes by ~20%
- Full-width buttons
- Single column grids

## ✅ Design Principles

1. **Consistency**: Use the gradient consistently for primary actions and branding
2. **Hierarchy**: Clear visual hierarchy with size, weight, and color
3. **Whitespace**: Generous padding and margins for readability
4. **Rounded Corners**: Soft, friendly appearance (10-30px radius)
5. **Subtle Shadows**: Light shadows for depth without heaviness
6. **Accessibility**: Sufficient contrast ratios, clear focus states

## 📋 Code Example

```clojure
(rum/defc example-section []
  [:div
   {:style {:background "#f8f9fa"
            :padding "80px 20px"}}
   [:div.flex.flex-column.items-center
    {:style {:max-width "1200px"
             :margin "0 auto"}}
    [:h2 {:style {:font-size "36px"
                  :font-weight "700"
                  :color "#1a1a2e"
                  :margin "0 0 48px 0"}}
     "Section Title"]
    [:div
     {:style {:display "grid"
              :grid-template-columns "repeat(auto-fit, minmax(280px, 1fr))"
              :gap "24px"
              :width "100%"}}
     ;; Cards here
     ]]])
```

---

**Last Updated**: 2026
**Version**: 1.0
