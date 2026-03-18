/* ============================================================
   SAVORY CLOUD — Interactive 3D Model Viewer (GitHub-style)
   Three.js + GLTFLoader · Mouse-tracking with Lerp
   ============================================================ */
import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { DRACOLoader } from "three/addons/loaders/DRACOLoader.js";


(function () {
  const canvas = document.getElementById("m3dCanvas");
  const container = document.getElementById("m3dContainer");
  const loaderUI = document.getElementById("m3dLoader");

  if (!canvas || !container) return;

  /* ─── Scene ─── */
  const scene = new THREE.Scene();

  /* ─── Camera ─── */
  const camera = new THREE.PerspectiveCamera(
    45,
    container.clientWidth / container.clientHeight,
    0.1,
    1000,
  );
  camera.position.set(0, 0, 5);

  /* ─── Renderer (transparent background) — GPU high-performance ─── */
  const renderer = new THREE.WebGLRenderer({
    canvas,
    alpha: true,
    antialias: true,
    powerPreference: "high-performance",
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(container.clientWidth, container.clientHeight);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.4;

  // WebGL memory audit
  const glInfo = renderer.info;

  /* ─── Lighting ─── */
  // Soft ambient fill
  const ambientLight = new THREE.AmbientLight(0xffffff, 0.7);
  scene.add(ambientLight);

  // Main key light
  const keyLight = new THREE.DirectionalLight(0xffffff, 1.4);
  keyLight.position.set(5, 5, 5);
  scene.add(keyLight);

  // Accent fill light (green tint from Savory Cloud branding)
  const fillLight = new THREE.DirectionalLight(0x38e07b, 0.35);
  fillLight.position.set(-4, 2, -3);
  scene.add(fillLight);

  // Rim/back light for silhouette edge
  const rimLight = new THREE.DirectionalLight(0xffffff, 0.6);
  rimLight.position.set(0, -3, -5);
  scene.add(rimLight);

  /* ─── Drag-to-Rotate State ─── */
  let isDragging = false;
  let prevMouseX = 0;
  let prevMouseY = 0;
  let dragRotX = 0; // accumulated rotation from drag
  let dragRotY = 0;
  let velocityX = 0; // inertia velocity
  let velocityY = 0;
  const SENSITIVITY = 0.005; // drag-to-rotation multiplier
  const LERP_FACTOR = 0.08; // smoothing for drag
  const INERTIA_DAMPING = 0.95; // how quickly inertia fades (closer to 1 = longer glide)

  // Mouse down → start dragging
  container.addEventListener("mousedown", (e) => {
    isDragging = true;
    prevMouseX = e.clientX;
    prevMouseY = e.clientY;
    velocityX = 0;
    velocityY = 0;
  });

  // Mouse move → accumulate rotation while dragging
  window.addEventListener(
    "mousemove",
    (e) => {
      if (!isDragging) return;
      const dx = e.clientX - prevMouseX;
      const dy = e.clientY - prevMouseY;
      dragRotY += dx * SENSITIVITY;
      dragRotX += dy * SENSITIVITY;
      dragRotX = Math.max(-Math.PI / 2.5, Math.min(Math.PI / 2.5, dragRotX));
      velocityX = dy * SENSITIVITY;
      velocityY = dx * SENSITIVITY;
      prevMouseX = e.clientX;
      prevMouseY = e.clientY;
    },
    { passive: true },
  );

  // Mouse up → stop drag, keep inertia
  window.addEventListener("mouseup", () => {
    isDragging = false;
  });

  // Touch support
  container.addEventListener(
    "touchstart",
    (e) => {
      isDragging = true;
      prevMouseX = e.touches[0].clientX;
      prevMouseY = e.touches[0].clientY;
      velocityX = 0;
      velocityY = 0;
      e.preventDefault();
    },
    { passive: false },
  );

  window.addEventListener(
    "touchmove",
    (e) => {
      if (!isDragging) return;
      e.preventDefault();
      const dx = e.touches[0].clientX - prevMouseX;
      const dy = e.touches[0].clientY - prevMouseY;
      dragRotY += dx * SENSITIVITY;
      dragRotX += dy * SENSITIVITY;
      dragRotX = Math.max(-Math.PI / 2.5, Math.min(Math.PI / 2.5, dragRotX));
      velocityX = dy * SENSITIVITY;
      velocityY = dx * SENSITIVITY;
      prevMouseX = e.touches[0].clientX;
      prevMouseY = e.touches[0].clientY;
    },
    { passive: false },
  );

  window.addEventListener("touchend", () => {
    isDragging = false;
  });

  /* ─── Model Group (for pivot centering) ─── */
  const pivot = new THREE.Group();
  scene.add(pivot);
  let model = null;

  /* ─── Load GLB Model (Draco-compressed) ─── */
  const dracoLoader = new DRACOLoader();
  dracoLoader.setDecoderPath(
    "https://www.gstatic.com/draco/versioned/decoders/1.5.6/",
  );
  dracoLoader.setDecoderConfig({ type: "js" });

  const gltfLoader = new GLTFLoader();
  gltfLoader.setDRACOLoader(dracoLoader);
  const modelPath = "/system-landing/modelo.glb";
  const loadStartTime = performance.now();


  gltfLoader.load(
    modelPath,
    (gltf) => {
      const loadEndTime = performance.now();

      model = gltf.scene;

      // Auto-scale: fit model into a ~2.8 unit bounding sphere
      const box = new THREE.Box3().setFromObject(model);
      const size = box.getSize(new THREE.Vector3());
      const center = box.getCenter(new THREE.Vector3());
      const maxDim = Math.max(size.x, size.y, size.z);
      const scale = 2.8 / maxDim;

      model.scale.setScalar(scale);

      // Center model at origin
      model.position.x = -center.x * scale;
      model.position.y = -center.y * scale;
      model.position.z = -center.z * scale;

      pivot.add(model);

      // Hide loading UI
      if (loaderUI) loaderUI.classList.add("is-hidden");

      // Log WebGL memory after model load
    },
    (xhr) => {
      if (xhr.lengthComputable) {
        const pct = Math.round((xhr.loaded / xhr.total) * 100);
        const pEl = loaderUI?.querySelector("p");
        if (pEl) pEl.textContent = `Cargando modelo… ${pct}%`;
      } else {
      }
    },
    (error) => {
      if (loaderUI) {
        loaderUI.innerHTML =
          '<p style="color:#ff6b6b;">Error al cargar el modelo 3D</p>';
      }
    },
  );

  /* ─── Idle slow rotation ─── */
  let idleAngle = 0;
  const IDLE_SPEED = 0.002; // radians per frame
  let currentRotX = 0; // smoothed rotation applied to pivot
  let currentRotY = 0;

  /* ─── Clock for delta time ─── */
  const clock = new THREE.Clock();

  /* ─── Visibility: pause render loop when off-screen ─── */
  let isVisible = false;
  let animFrameId = null;

  const visObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        isVisible = entry.isIntersecting;
        if (isVisible && !animFrameId) {
          clock.getDelta(); // flush stale delta
          animate();
        } else if (!isVisible && animFrameId) {
          cancelAnimationFrame(animFrameId);
          animFrameId = null;
        }
      });
    },
    { threshold: 0.0, rootMargin: "100px" },
  );
  visObserver.observe(container);

  /* ─── Animation Loop (only runs when visible) ─── */
  function animate() {
    animFrameId = requestAnimationFrame(animate);
    const delta = clock.getDelta();
    const lerpFactor = 1 - Math.pow(1 - LERP_FACTOR, delta * 60);

    // Apply inertia when not dragging
    if (!isDragging) {
      dragRotY += velocityY;
      dragRotX += velocityX;
      dragRotX = Math.max(-Math.PI / 2.5, Math.min(Math.PI / 2.5, dragRotX));
      velocityX *= INERTIA_DAMPING;
      velocityY *= INERTIA_DAMPING;
      // Stop tiny residual motion
      if (Math.abs(velocityX) < 0.0001) velocityX = 0;
      if (Math.abs(velocityY) < 0.0001) velocityY = 0;
    }

    // Idle auto-rotation (pauses while dragging or inertia is active)
    const hasInertia =
      Math.abs(velocityX) > 0.0001 || Math.abs(velocityY) > 0.0001;
    if (!isDragging && !hasInertia) {
      idleAngle += IDLE_SPEED;
    }

    // Lerp smoothly toward target rotation
    currentRotX += (dragRotX - currentRotX) * lerpFactor;
    currentRotY += (dragRotY - currentRotY) * lerpFactor;

    // Apply rotation to pivot group
    pivot.rotation.x = currentRotX;
    pivot.rotation.y = currentRotY + idleAngle;

    renderer.render(scene, camera);
  }

  // Render loop is started/stopped by IntersectionObserver above

  /* ─── Resize Handler ─── */
  function onResize() {
    const w = container.clientWidth;
    const h = container.clientHeight;
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    renderer.setSize(w, h);
  }

  window.addEventListener("resize", onResize);

  // Also handle container resizes more responsively
  if ("ResizeObserver" in window) {
    new ResizeObserver(onResize).observe(container);
  }
})();
