# Own widget picker instead of the system picker

Status: accepted (supersedes the "No custom picker" rule in PRD 7.4)

Widget handling mimics AOSP Launcher3 (Android 17). Launcher3 never uses the system `ACTION_APPWIDGET_PICK` picker: it ships its own picker sheet with previews, per-app groups, search and tap-to-add or drag-to-place. The system picker is a plain list with no previews and cannot hand a drag to the home screen, so we build our own Compose picker and drop `ACTION_APPWIDGET_PICK`.

## Consequences

- Like Launcher3, the picker is its own Activity and not a sheet inside the Home Activity. The pin sheet must be a separate Activity anyway, because it appears over the requesting app. A drag that starts there has to reach Home through system drag-and-drop (`DRAG_FLAG_GLOBAL`). The picker uses the same path, so there is one drag hand-off instead of two. Home turns the incoming drag into its normal move and reflow.

- The launcher binds widgets itself: `bindAppWidgetIdIfAllowed`, falling back to the system `ACTION_APPWIDGET_BIND` dialog. Nothing binds on our behalf any more.
- Previews come from `previewLayout`, then `previewImage`, then a placeholder. Generated previews (API 35) are used only when the framework returns them, which needs the launcher to be bind-allow-listed (the user ticked "always allow" once).
