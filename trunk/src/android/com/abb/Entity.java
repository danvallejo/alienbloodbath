// Copyright 2008 and onwards Matthew Burkhart.
//
// This program is free software; you can redistribute it and/or modify it under
// the terms of the GNU General Public License as published by the Free Software
// Foundation; version 3 of the License.
//
// This program is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
// FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
// details.

package android.com.abb;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;


public class Entity {
  public Bitmap sprite;
  public Rect sprite_source;

  public boolean alive = true;  // Should not be deleted from the game.

  public float radius;
  public boolean has_ground_contact;

  public float x;    // Position.
  public float y;
  public float dx;   // Velocity.
  public float dy;
  public float ddx;  // Acceleration.
  public float ddy;

  public void Stop() {
    dx = dy = ddx = ddy = 0.0f;
  }

  public void Step(float time_step) {
    x += 0.5f * ddx * time_step * time_step + dx * time_step;
    y += 0.5f * ddy * time_step * time_step + dy * time_step;
    dx += ddx * time_step;
    dy += ddy * time_step;

    // The following is a poor hack to simulate "friction" against the ground
    // surface. The problem with this implementation is that it does not account
    // for the time_step. TODO: Fix.
    if (has_ground_contact) {
      dx *= (1.0f - kGroundFriction);
    }
  }

  /** Draw the entity to the canvas such that the specified coordinates are
   * centered. */
  public void Draw(Canvas canvas, float center_x, float center_y) {
    if (sprite != null) {
      int canvas_width = canvas.getWidth();
      int canvas_height = canvas.getHeight();

      RectF sprite_destination =
          new RectF(0, 0, sprite_source.width(), sprite_source.height());
      sprite_destination.offset(
          x - center_x - sprite_source.width() / 2.0f + canvas_width / 2.0f,
          y - center_y - sprite_source.height() / 2.0f + canvas_height / 2.0f);
      canvas.drawBitmap(sprite, sprite_source, sprite_destination, paint_);
    }
  }

  private Paint paint_ = new Paint();  // Drawing settings.

  private static final float kGroundFriction = 0.1f;
}
