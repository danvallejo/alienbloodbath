// Copyright 2008 and onwards Matthew Burkhart and Matt Barnes.
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;


public class GameState implements Game {
  public Avatar avatar = new Avatar(this);
  public ArrayList<Entity> enemies = new ArrayList<Entity>();
  public int enemy_sprites;
  public Map map = new Map(this);
  public int misc_sprites;
  public ArrayList<Entity> particles = new ArrayList<Entity>();
  public ArrayList<Entity> projectiles = new ArrayList<Entity>();

  public GameState(Context context) {
    mContext = context;
    mVibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
  }

  public void initializeGraphics(Graphics graphics) {
    avatar.loadFromUri(Uri.parse("content:///humanoid.entity"));

    String enemy_sprites_path =
        Content.getTemporaryFilePath(Uri.parse("content:///enemy_0.png"));
    Bitmap enemy_sprites_bitmap = BitmapFactory.decodeFile(enemy_sprites_path);
    enemy_sprites = graphics.loadImageFromBitmap(enemy_sprites_bitmap);

    String misc_sprites_path =
        Content.getTemporaryFilePath(Uri.parse("content:///misc.png"));
    Bitmap misc_sprites_bitmap = BitmapFactory.decodeFile(misc_sprites_path);
    misc_sprites = graphics.loadImageFromBitmap(misc_sprites_bitmap);
  }

  /** Initialize the game state structure. Upon returning the game state should
   * be in a state representing a new game "life". */
  public void reset() {
    map.reload();
    particles.clear();
    projectiles.clear();
    enemies.clear();
    avatar.stop();
    avatar.alive = true;
    avatar.x = map.getStartingX();
    avatar.y = map.getStartingY();
    mViewX = mTargetViewX = avatar.x;
    mViewY = mTargetViewY = avatar.y;
    mDeathTimer = kDeathTimer;
  }

  public boolean onKeyDown(int key_code) {
    avatar.setKeyState(key_code, 1);
    return false;  // False to indicate not handled.
  }

  public boolean onKeyUp(int key_code) {
    avatar.setKeyState(key_code, 0);
    return false;  // False to indicate not handled.
  }

  public boolean onFrame(Graphics graphics, float time_step) {
    stepGame(time_step);
    drawGame(graphics);
    return true;  // True to keep updating.
  }

  /** Run the game simulation for the specified amount of seconds. */
  protected void stepGame(float time_step) {
    // Update the view parameters.
    if (!avatar.has_ground_contact) {
      mTargetZoom = kAirZoom;
    } else {
      mTargetZoom = kGroundZoom;
    }
    mTargetViewX = avatar.x + kViewLead * avatar.dx;
    mTargetViewY = avatar.y + kViewLead * avatar.dy;

    mZoom += (mTargetZoom - mZoom) * kZoomSpeed;
    mViewX += (mTargetViewX - mViewX) * kViewSpeed;
    mViewY += (mTargetViewY - mViewY) * kViewSpeed;

    // Step the avatar.
    if (avatar.alive) {
      avatar.step(time_step);
      avatar.mWeapon.step(time_step);
      map.collideEntity(avatar);
      if (Map.tileIsGoal(map.tileAt(avatar.x, avatar.y))) {
        map.advanceLevel();
        reset();
      }
    } else {
      if (mDeathTimer == kDeathTimer) {
        for (int n = 0; n < 2 * kBloodBathSize; n++) {
          createBloodParticle(
              avatar.x, avatar.y,
              2.0f * kBloodBathVelocity * (0.5f - mRandom.nextFloat()) + avatar.dx,
              2.0f * kBloodBathVelocity * (0.5f - mRandom.nextFloat()) + avatar.dy);
        }
      }
      mDeathTimer -= time_step;
      if (mDeathTimer < 0) {
        reset();
      }
    }

    // Step the enemies.
    for (Iterator it = enemies.iterator(); it.hasNext();) {
      Enemy enemy = (Enemy)it.next();
      enemy.step(time_step);
      map.collideEntity(enemy);
      if (!enemy.alive) {
        vibrate();
        for (int n = 0; n < kBloodBathSize; n++) {
          createBloodParticle(
              enemy.x, enemy.y,
              kBloodBathVelocity * (0.5f - mRandom.nextFloat()) + enemy.dx,
              kBloodBathVelocity * (0.5f - mRandom.nextFloat()) + enemy.dy);
        }
        it.remove();
      }
    }

    // Step the projectiles and collide them against the enemies.
    for (Iterator it = projectiles.iterator(); it.hasNext();) {
      Fire projectile = (Fire)it.next();
      projectile.step(time_step);
      for (Iterator enemy_it = enemies.iterator(); enemy_it.hasNext();) {
        projectile.collideEntity((Entity)enemy_it.next());
      }
      if (!projectile.alive) {
        it.remove();
      }
    }

    // Step the particles.
    for (Iterator it = particles.iterator(); it.hasNext();) {
      Entity particle = (Entity)it.next();
      particle.step(time_step);
      if (!particle.alive) {
        it.remove();
      }
    }
  }

  /** Draw the game state. The game map and entities are always drawn with the
   * avatar centered in the screen. */
  protected void drawGame(Graphics graphics) {
    // Draw the map tiles.
    map.draw(graphics, mViewX, mViewY, mZoom);

    // Draw the enemies.
    for (Iterator it = enemies.iterator(); it.hasNext();) {
      ((Entity)it.next()).draw(graphics, mViewX, mViewY, mZoom);
    }

    // Draw the avatar and weapon.
    if (avatar.alive) {
      avatar.draw(graphics, mViewX, mViewY, mZoom);
      avatar.mWeapon.setImage(graphics);
      //avatar.mWeapon.draw(graphics, mViewX, mViewY, mZoom);
    }

    // Draw the projectiles.
    for (Iterator it = projectiles.iterator(); it.hasNext();) {
      ((Entity)it.next()).draw(graphics, mViewX, mViewY, mZoom);
    }

    // Draw the particles.
    for (Iterator it = particles.iterator(); it.hasNext();) {
      ((Entity)it.next()).draw(graphics, mViewX, mViewY, mZoom);
    }
  }

  public Entity createEnemy(float x, float y) {
    Entity enemy = new Enemy(avatar);
    enemy.sprite_image = enemy_sprites;
    enemy.x = x;
    enemy.y = y;
    enemies.add(enemy);
    return enemy;
  }

  public Entity createBloodParticle(float x, float y, float dx, float dy) {
    Entity blood = new Blood();
    blood.sprite_image = misc_sprites;
    blood.x = x;
    blood.y = y;
    blood.dx = dx;
    blood.dy = dy;
    blood.ddy = kGravity;
    particles.add(blood);
    return blood;
  }

  public Entity createFireProjectile(float x, float y, float dx, float dy) {
    Entity fire = new Fire();
    fire.sprite_image = misc_sprites;
    fire.x = x;
    fire.y = y;
    fire.dx = dx;
    fire.dy = dy;
    fire.ddy = -50.0f;  // Slight up draft.
    projectiles.add(fire);
    return fire;
  }

  public void vibrate() {
    mVibrator.vibrate(kVibrateLength);
  }

  public void loadStateBundle(Bundle saved_instance_state) {
    map.loadStateBundle(saved_instance_state.getBundle("map"));
    reset();

    mTargetViewX = saved_instance_state.getFloat("mTargetViewX");
    mTargetViewY = saved_instance_state.getFloat("mTargetViewY");
    mViewX = saved_instance_state.getFloat("mViewX");
    mViewY = saved_instance_state.getFloat("mViewY");
    mZoom = saved_instance_state.getFloat("mZoom");

    avatar.x = saved_instance_state.getFloat("avatar.x");
    avatar.y = saved_instance_state.getFloat("avatar.y");
    avatar.dx = saved_instance_state.getFloat("avatar.dx");
    avatar.dy = saved_instance_state.getFloat("avatar.dy");
    avatar.ddx = saved_instance_state.getFloat("avatar.ddx");
    avatar.ddy = saved_instance_state.getFloat("avatar.ddy");
    avatar.alive = saved_instance_state.getBoolean("avatar.alive");
  }

  public Bundle saveStateBundle() {
    // Note that particles, projectiles and spawned enemies are lost through
    // serialization.
    Bundle saved_instance_state = new Bundle();
    saved_instance_state.putBundle("map", map.saveStateBundle());

    saved_instance_state.putFloat("mTargetViewX", mTargetViewX);
    saved_instance_state.putFloat("mTargetViewY", mTargetViewY);
    saved_instance_state.putFloat("mViewX", mViewX);
    saved_instance_state.putFloat("mViewY", mViewY);
    saved_instance_state.putFloat("mZoom", mZoom);

    saved_instance_state.putFloat("avatar.x", avatar.x);
    saved_instance_state.putFloat("avatar.y", avatar.y);
    saved_instance_state.putFloat("avatar.dx", avatar.dx);
    saved_instance_state.putFloat("avatar.dy", avatar.dy);
    saved_instance_state.putFloat("avatar.dx", avatar.ddx);
    saved_instance_state.putFloat("avatar.dy", avatar.ddy);
    saved_instance_state.putBoolean("avatar.alive", avatar.alive);
    return saved_instance_state;
  }

  private Context mContext;
  private float mDeathTimer = kDeathTimer;
  private Random mRandom = new Random();
  private float mTargetViewX = 0.0f;
  private float mTargetViewY = 0.0f;
  private float mTargetZoom = kGroundZoom;
  private Vibrator mVibrator;
  private float mViewX = 0.0f;
  private float mViewY = 0.0f;
  private float mZoom = kGroundZoom;

  private static final float kAirZoom = 0.6f;
  private static final int kBloodBathSize = 10;  // Number of blood particles.
  private static final float kBloodBathVelocity = 60.0f;
  private static final float kDeathTimer = 4.0f;
  private static final float kGravity = 200.0f;
  private static final float kGroundZoom = 0.8f;
  private static final long kVibrateLength = 30;  // Milliseconds.
  private static final float kViewLead = 1.0f;
  private static final float kViewSpeed = 0.1f;
  private static final float kZoomSpeed = 0.05f;
}
