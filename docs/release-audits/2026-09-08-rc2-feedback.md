# v0.1.4-rc.2 real-device feedback

Observed on-device after v0.1.4-rc.2:

- The right-edge swipe conflicts with Android system back gesture navigation.
- Replace the edge-start gesture with a small, movable side handle similar to an edge-panel affordance. The handle should sit inward from the physical edge, be vertically draggable, and open Midroid controls by tap or inward drag.
- Notification scaling is improved, but grouped reaction notifications still show uneven/clipped placement. Keep enlarged avatars and reaction artwork while binding each grouped reaction to its actual avatar/reaction pair and wrapping those pairs cleanly inside the notification row.
- Timeline reaction scaling is already improved and should remain unchanged.

## Implemented follow-up

- Reworked `EdgeSwipeMenuLayout` into an inset movable side-handle surface. The saved vertical position is restored across launches and the handle no longer requires a gesture that begins at the Android system-back edge.
- Hardened grouped-reaction DOM discovery by walking outward from actual reaction artwork instead of classifying every two-child `div` in the notification tail.
- Render grouped reaction items as compact vertical avatar/reaction stacks with bounded artwork width and wrapping, preventing one wide emoji from consuming the entire notification row.
