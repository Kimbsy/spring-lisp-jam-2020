(ns lambdahoy.sprite.ship
  (:require [lambdahoy.sound :as sound]
            [lambdahoy.sprite :as sprite]
            [lambdahoy.sprite.cannon :as cannon]
            [lambdahoy.sprite.projectile :as projectile]
            [lambdahoy.utils :as u]
            [quil.core :as q]))

(defn ->ship
  [pos & {:keys [r vel rvel pc? crew cannons]
          :or   {r       0
                 vel     [0 0]
                 rvel    0
                 pc?     false
                 crew    []
                 cannons []}}]
  {:pos     pos
   :vel     vel
   :r       r
   :rvel    rvel
   :speed   (u/magnitude vel)
   :image   (q/load-image "images/ship-small.png")
   :pc?     pc?
   :crew    crew
   :cannons cannons

   :npc-command {:direction :nil
                 :duration  50}})

(defn rvel-drift
  [rvel]
  (cond (< rvel 0) 1
        (> rvel 0) -1
        :else      0))

(defn update-rvel
  [{:keys [rvel]} held-keys]
  (let [drift      0.025
        back-drift (* drift (rvel-drift rvel))]
    (cond (held-keys :right)
          (min (+ rvel 0.1) 2)

          (held-keys :left)
          (max (- rvel 0.1) -2)

          (> drift (Math/abs (+ rvel back-drift)))
          0

          :else
          (+ rvel back-drift))))

(defn update-speed
  [{:keys [speed]} held-keys]
  (cond (held-keys :up)
        (min (+ speed 0.1) 10)

        (held-keys :down)
        (max (- speed 0.1) 0)

        :else
        (max (- speed 0.05) 0)))

(defn update-if-pc-ship
  [ship held-keys]
  (if (:pc? ship)
    (-> ship
        (assoc :rvel (update-rvel ship held-keys))
        (assoc :speed (update-speed ship held-keys)))
    ship))

(defn ->npc-command
  []
  {:direction (rand-nth [:left :right nil])
   :duration (+ 10 (rand-int 50))})

(defn update-command
  [command]
  (if (= 0 (:duration command))
    (->npc-command)
    (update command :duration dec)))

(defn update-if-npc-ship
  [{:keys [npc-command] :as ship}]
  (if-not (:pc? ship)
    (-> ship
        (update :npc-command update-command)
        (assoc :rvel (update-rvel ship #{(:direction npc-command)})))
    ship))

(defn update-self
  [{:keys [held-keys]} {:keys [pos vel r speed crew] :as ship}]
  (-> ship
      (update-if-pc-ship held-keys)
      update-if-npc-ship
      (assoc :vel (u/velocity-vector ship))
      (assoc :pos (map + pos vel))
      (update :r #(mod (+ % (:rvel ship)) 360))
      (update :crew #(map sprite/update-self %))))

(defn draw-self
  [{:keys [pos r image crew cannons] :as ship}]
  (let [[x y] pos]
    (u/wrap-trans-rot
     x y r
     #(do
        (q/image-mode :center)
        (q/image image 0 0)
        (doall (map sprite/draw-animated-sprite crew))
        (doall (map cannon/draw-self cannons))))))

(defn cannon-rotational-velocity
  "Determine the current velocity vector of a cannon based on the
  rotational velocity of the ship and the position of the cannon in
  relation to the center of rotation."
  [cannon rvel ship-rotation]
  (let [r           (:pos cannon)                         ; radius vector
        r-length    (u/magnitude r)                       ; length of radius vector
        c           (* 2 Math/PI r-length)                ; circumference
        w           (/ rvel 360)                          ; angular velocity (rotations per frame)
        speed       (* w c)                               ; linear speed of cannon
        cannon-direction (u/direction-vector (+ ship-rotation (u/rotation-angle (:pos cannon))))
        orthogonals (map u/unit-vector [cannon-direction (map (partial * -1) cannon-direction)])
        direction   (cond
                      (< 0 rvel)   (last orthogonals)  ; rotating clockwise
                      (< rvel 0)   (first orthogonals) ; rotating anticlockwise
                      (zero? rvel) [0 0])              ; not rotating
        velocity    (map (partial * speed) direction)]
    velocity))

;; @TODO: maybe firing should be based on held keys? once we implement cannon firing delays?
(defn fire
  "Create a vector of projectiles as a result of firing all cannons on a
  ship."
  [{:keys [pos vel r rvel cannons] :as s}]
  (sound/play-sound-effect :cannon)
  (let [cannon-rvels (map #(cannon-rotational-velocity % rvel r) cannons)
        direction    (u/direction-vector r)
        orthogonals  (u/orthogonals direction)]
    (->> cannons
         ;; firing direction
         (map (fn [{[x y] :pos}] (if (pos? x)
                                   (last (u/orthogonals direction))
                                   (first (u/orthogonals direction)))))

         ;; default projectile speed
         (map (u/scale-by 10))

         ;; add ship velocity
         (map (partial map + vel))

         ;; account for cannon rotational velocity
         (map (partial map +) cannon-rvels)

         ;; create projectile
         (map #(projectile/->projectile pos :vel %)))))

(defn key-pressed
  [state e]
  (if (= :space (:key e))
    (let [pc-ships (filter :pc? (get-in state [:sprites :ocean :ships]))]
      (update-in state
                 [:sprites :ocean :projectiles]
                 #(take 100 (concat (apply concat (map fire pc-ships))
                                    %))))
    state))
