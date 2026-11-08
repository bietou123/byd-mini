import * as THREE from 'three';

// v4：分带建模（下部车身 / 上部侧围 / 车顶）做出真实棱线，
//     轮拱按椭圆弧真正切出来，玻璃改成独立的贴合薄片。

const canvas = document.getElementById('c');
const renderer = new THREE.WebGLRenderer({
  canvas, antialias: true, alpha: true,
  preserveDrawingBuffer: true, powerPreference: 'high-performance'
});
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.02;
renderer.outputColorSpace = THREE.SRGBColorSpace;

const scene = new THREE.Scene();

// ───────────── 环境 ─────────────
// 参考车展棚的布光：顶部一块大柔光箱罩住车顶和引擎盖，车身两侧各立一条长灯带。
// 车门上那道从上往下的长高光就是竖灯带打出来的，光看材质调参永远调不出这种「形」。
// three 自带的 RoomEnvironment 是个房间而不是棚子，灯板小而散，
// 反射出来只是一片糊亮的雾，车漆看着像塑料。
function studioEnv() {
  const s = new THREE.Scene();
  const R = 12;
  const bgGeo = new THREE.SphereGeometry(R, 44, 30);
  const pos = bgGeo.attributes.position;
  const col = [];
  // 水平方向必须压暗：车身侧围的法线接近水平，它反射的就是天球「赤道」那一圈。
  // 之前赤道亮度给到 0.20 再叠两块正对侧面的超亮灯板，整个侧围被冲成一片死白，
  // 侧窗玻璃（深色底）完全被反射盖住，看上去像贴了一块白塑料。
  const skyTop = new THREE.Color(0.44, 0.46, 0.52);
  const skyMid = new THREE.Color(0.085, 0.090, 0.105);
  const ground = new THREE.Color(0.014, 0.015, 0.018);
  const tmp = new THREE.Color();
  for (let i = 0; i < pos.count; i++) {
    const y = pos.getY(i) / R;
    if (y >= 0) tmp.copy(skyMid).lerp(skyTop, Math.pow(y, 0.85));
    else tmp.copy(skyMid).lerp(ground, Math.min(1, -y * 2.4));
    col.push(tmp.r, tmp.g, tmp.b);
  }
  bgGeo.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  s.add(new THREE.Mesh(bgGeo, new THREE.MeshBasicMaterial({
    vertexColors: true, side: THREE.BackSide, depthWrite: false
  })));

  // 亮度用 >1 的线性值，PMREM 会原样烘进 HDR 环境图里
  const panel = (w, h, rgb, p, rot) => {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h),
      new THREE.MeshBasicMaterial({
        color: new THREE.Color(rgb[0], rgb[1], rgb[2]),
        side: THREE.DoubleSide
      }));
    m.position.set(p[0], p[1], p[2]);
    if (rot) m.rotation.set(rot[0], rot[1], rot[2]);
    s.add(m);
  };
  // 顶部主柔光箱：比车长还大，引擎盖/车顶那片大面积渐变高光就来自它
  panel(9.0, 7.4, [3.8, 3.9, 4.2], [0, 7.0, 0], [-Math.PI / 2, 0, 0]);
  // 左右 strip box：细长竖条（沿 x 窄、沿 y 高）立在车身两侧。
  // 车门上那道纵向长高光必须是「竖条灯」才打得出来——之前这里放的是横条，
  // 反射出来是横穿车身的亮带，把纵向的型面层次全糊掉了。
  panel(1.1, 5.2, [4.6, 4.7, 5.0], [0, 2.5, 6.4], [0, Math.PI, 0]);
  panel(1.1, 5.2, [2.4, 2.5, 2.8], [0, 2.5, -6.4], [0, 0, 0]);
  // 前后各一块弱补光：只给车头/车尾一点轮廓，不参与侧面反射
  panel(5.0, 2.6, [1.9, 2.0, 2.2], [6.6, 2.2, 0], [0, -Math.PI / 2, 0]);
  panel(5.0, 2.6, [1.2, 1.3, 1.5], [-6.6, 2.2, 0], [0, Math.PI / 2, 0]);
  // 地台回弹：不让车身下沿和轮眉内侧变成死黑
  panel(20, 20, [0.42, 0.45, 0.52], [0, -1.5, 0], [-Math.PI / 2, 0, 0]);
  return s;
}

const pmrem = new THREE.PMREMGenerator(renderer);
const envRT = pmrem.fromScene(studioEnv(), 0.03);
scene.environment = envRT.texture;
scene.environmentIntensity = 1.15;

const camera = new THREE.PerspectiveCamera(17, 1, 0.1, 40);
// 默认视角对齐懂车帝那套：前 3/4（约 az 40°）、低仰角 11°、长焦压缩 17°
const CAM = { x: -2.179, y: 0.699, z: 1.830, tx: 0.010, ty: 0.140, tz: 0 };
camera.position.set(CAM.x, CAM.y, CAM.z);
camera.lookAt(CAM.tx, CAM.ty, CAM.tz);

// ───────────── 材质 ─────────────
const PAINTS = {
  deepblue: { color: 0x1a3f63, metalness: 0.55, roughness: 0.30 },
  black:    { color: 0x101216, metalness: 0.62, roughness: 0.28 },
  silver:   { color: 0xb9c0c8, metalness: 0.85, roughness: 0.22 },
  white:    { color: 0xe8eaee, metalness: 0.28, roughness: 0.24 }
};
let paintKey = 'deepblue';

// 车漆：显式反射强度压到 0.9、清漆层粗糙度提到 0.10。
// 原来 envMapIntensity 1.35 + clearcoatRoughness 0.04，侧围中段会反射出
// 一整块死白的「油光斑」，是整车廉价感的主要来源。
const paintMat = new THREE.MeshPhysicalMaterial({
  color: PAINTS[paintKey].color,
  metalness: PAINTS[paintKey].metalness,
  roughness: 0.32,
  clearcoat: 1.0, clearcoatRoughness: 0.11,
  envMapIntensity: 0.78
});
// 玻璃：暗色、半哑。暗玻璃靠 Fresnel 出反射，不靠金属度。
// 之前的 clearcoat(0.09) + base(0.135) 是两层近似镜面，RoomEnvironment 的顶部
// 灯板被原样镜像出来，侧窗就成了三条横向亮带。现在两层都磨到 0.24 左右，
// 反射摊成一片柔和的亮雾，读起来才是「暗色隔热玻璃」而不是「镜面胶带」。
const glassMat = new THREE.MeshPhysicalMaterial({
  color: 0x0d141c, metalness: 0.06, roughness: 0.24,
  clearcoat: 0.45, clearcoatRoughness: 0.24, envMapIntensity: 0.95,
  side: THREE.DoubleSide
});
const trimMat = new THREE.MeshStandardMaterial({ color: 0x0a0b0d, metalness: 0.35, roughness: 0.72 });
const pianoMat = new THREE.MeshPhysicalMaterial({
  color: 0x08090c, metalness: 0.4, roughness: 0.10, clearcoat: 1, clearcoatRoughness: 0.04,
  envMapIntensity: 1.4
});
const chromeMat = new THREE.MeshStandardMaterial({ color: 0xd6dce4, metalness: 1.0, roughness: 0.14, envMapIntensity: 1.7 });
const tireMat = new THREE.MeshStandardMaterial({ color: 0x121317, metalness: 0.04, roughness: 0.92 });
const rimMat = new THREE.MeshStandardMaterial({ color: 0xaab0b9, metalness: 1.0, roughness: 0.19, envMapIntensity: 1.6 });
const rimDarkMat = new THREE.MeshStandardMaterial({ color: 0x1e2126, metalness: 0.88, roughness: 0.36 });
const discMat = new THREE.MeshStandardMaterial({ color: 0x35393f, metalness: 0.82, roughness: 0.44 });
const headMat = new THREE.MeshPhysicalMaterial({
  color: 0xdfeaf6, emissive: 0x9fcaff, emissiveIntensity: 1.0,
  metalness: 0.55, roughness: 0.10, clearcoat: 1, clearcoatRoughness: 0.03
});
const tailMat = new THREE.MeshPhysicalMaterial({
  color: 0x8c0f16, emissive: 0xff2028, emissiveIntensity: 1.0,
  metalness: 0.4, roughness: 0.16, clearcoat: 1, clearcoatRoughness: 0.05
});

// ───────────── 插值 ─────────────
function pchip(k, x) {
  const n = k.length;
  if (x <= k[0][0]) return k[0][1];
  if (x >= k[n - 1][0]) return k[n - 1][1];
  let i = 0;
  while (i < n - 2 && x > k[i + 1][0]) i++;
  const h = k[i + 1][0] - k[i][0], t = (x - k[i][0]) / h;
  const t2 = t * t, t3 = t2 * t;
  return (2 * t3 - 3 * t2 + 1) * k[i][1]
    + (t3 - 2 * t2 + t) * h * slope(k, i)
    + (-2 * t3 + 3 * t2) * k[i + 1][1]
    + (t3 - t2) * h * slope(k, i + 1);
}
function slope(k, i) {
  const n = k.length;
  const d = j => (k[j + 1][1] - k[j][1]) / (k[j + 1][0] - k[j][0]);
  if (i === 0) return d(0);
  if (i === n - 1) return d(n - 2);
  const d0 = d(i - 1), d1 = d(i);
  if (d0 * d1 <= 0) return 0;
  const w1 = 2 * (k[i + 1][0] - k[i][0]) + (k[i][0] - k[i - 1][0]);
  const w2 = (k[i + 1][0] - k[i][0]) + 2 * (k[i][0] - k[i - 1][0]);
  return (w1 + w2) / (w1 / d0 + w2 / d1);
}

// ───────────── 轮廓（车长归一化 1.0） ─────────────
// 标定基准（车长归一化 1.0 = 4780mm）：
//   车高 1495 → 0.3128，离地 150 → 0.0314，轴距 2790 → 0.5837，
//   轮胎半径 334 → 0.0699。纵向尺寸本来就是准的，出问题的是角度。
const K_TOP = [
  // 引擎盖：前缘 875mm，到 cowl 抬到 1067mm（坡度 4°）
  [-0.500, 0.1830], [-0.465, 0.1878], [-0.425, 0.1932], [-0.380, 0.1990],
  [-0.330, 0.2052], [-0.280, 0.2112], [-0.235, 0.2168], [-0.190, 0.2232],
  // 前风挡：cowl(-0.190) → 车顶(-0.010)，投影 0.180、高差 0.086 → 25.5°。
  // 原来只有 18°，风挡太躺，A 柱被拉成一条斜穿半个车身的长条。
  [-0.155, 0.2385], [-0.115, 0.2565], [-0.070, 0.2785], [-0.030, 0.2962],
  [0.005, 0.3062], [0.050, 0.3110], [0.100, 0.3114], [0.140, 0.3095],
  // 后风挡：23° 溜背，下沿落在尾箱盖
  [0.180, 0.2980], [0.220, 0.2842], [0.260, 0.2688], [0.295, 0.2538],
  [0.320, 0.2432], [0.345, 0.2358],
  // 尾箱盖：几乎水平，末端轻微下压
  [0.385, 0.2300], [0.435, 0.2278], [0.500, 0.2268]
];

const K_WAIST = [
  [-0.500, 0.1460], [-0.460, 0.1600], [-0.410, 0.1725], [-0.360, 0.1825],
  [-0.310, 0.1905], [-0.260, 0.1985], [-0.214, 0.2075],
  [-0.170, 0.2165], [-0.100, 0.2232], [0.000, 0.2268],
  [0.100, 0.2290], [0.200, 0.2305], [0.290, 0.2310],
  [0.370, 0.2300], [0.440, 0.2262], [0.500, 0.2075]
];

const K_BOT = [
  [-0.500, 0.0470], [-0.470, 0.0390], [-0.430, 0.0330], [-0.380, 0.0315],
  [0.000, 0.0315], [0.360, 0.0315], [0.430, 0.0340], [0.470, 0.0392],
  [0.500, 0.0580]
];

const K_WB = [
  [-0.500, 0.1640], [-0.470, 0.1745], [-0.430, 0.1825], [-0.380, 0.1892],
  [-0.340, 0.1928], [-0.300, 0.1942], [-0.240, 0.1940], [-0.140, 0.1938],
  [-0.040, 0.1938], [0.060, 0.1940], [0.160, 0.1942], [0.250, 0.1942],
  [0.310, 0.1930], [0.380, 0.1905], [0.440, 0.1830], [0.480, 0.1720],
  [0.500, 0.1520]
];

// 车顶侧沿 / 引擎盖边缘的半宽。
// 原来座舱段只有 0.145（699mm），比腰线窄了 232mm —— 侧围上半截成了一面
// 大幅内倾的斜面，正侧视图里原样反射天空，就是那顶「银色帽子」。
// 真车（宽 1837）车顶侧沿约 780mm，这里取 0.161（770mm），只比腰线窄 160mm。
const K_WT = [
  [-0.500, 0.1530], [-0.465, 0.1608], [-0.425, 0.1656], [-0.380, 0.1680],
  [-0.330, 0.1688], [-0.280, 0.1680], [-0.235, 0.1660],
  [-0.190, 0.1638], [-0.140, 0.1622], [-0.080, 0.1612], [-0.010, 0.1608],
  [0.045, 0.1608], [0.110, 0.1612], [0.170, 0.1622],
  [0.240, 0.1642], [0.310, 0.1662], [0.370, 0.1672], [0.430, 0.1660],
  [0.500, 0.1560]
];

// ───────────── 关键尺寸 ─────────────
const AXLE_F = -0.3060, AXLE_R = 0.2710;
const TIRE_R = 0.0715;
const ARCH_DW = 0.0845;                // 轮拱半宽
const ARCH_TOP = TIRE_R + 0.0895;      // 轮拱顶
const WHEEL_Z = 0.1625, TIRE_HW = 0.0242;

function archY(x) {
  const rc = pchip(K_BOT, x);
  let best = rc;
  for (const a of [AXLE_F, AXLE_R]) {
    const d = (x - a) / ARCH_DW;
    if (Math.abs(d) < 1) {
      const v = rc + (ARCH_TOP - rc) * Math.sqrt(1 - d * d);
      if (v > best) best = v;
    }
  }
  return best;
}

const prof = x => ({
  bot: pchip(K_BOT, x),
  waist: pchip(K_WAIST, x),
  top: pchip(K_TOP, x),
  wb: pchip(K_WB, x),
  wt: pchip(K_WT, x)
});

const RAIL_DROP = 0.011;                 // 车顶侧沿相对中线的落差

// ───────────── 二维贝塞尔 ─────────────
function bez3(p0, p1, p2, p3, t) {
  const u = 1 - t, u2 = u * u, t2 = t * t;
  return [
    u2 * u * p0[0] + 3 * u2 * t * p1[0] + 3 * u * t2 * p2[0] + t2 * t * p3[0],
    u2 * u * p0[1] + 3 * u2 * t * p1[1] + 3 * u * t2 * p2[1] + t2 * t * p3[1]
  ];
}
function seg(out, p0, p1, p2, p3, n, skipFirst) {
  for (let i = skipFirst ? 1 : 0; i <= n; i++) out.push(bez3(p0, p1, p2, p3, i / n));
}
function line(out, p0, p1, n, skipFirst) {
  for (let i = skipFirst ? 1 : 0; i <= n; i++) {
    const t = i / n;
    out.push([p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t]);
  }
}

// ───────────── 截面：下部车身 ─────────────
function lowerPts(x) {
  const p = prof(x);
  const rc = p.bot;
  const ay = archY(x);
  const wb = p.wb, wa = p.waist;

  // 轮拱开度 0→1：所有轮拱相关几何都靠它渐变，
  // 保证前后端 / 门槛位置退化成一块平整侧裙，而不是凭空多出一道台阶。
  const open = Math.max(0, Math.min(1, (ay - rc) / Math.max(1e-4, ARCH_TOP - rc)));
  const flare = Math.min(1, open * 1.9);
  // 拱唇外沿：闭合时贴门槛(0.171)，拱顶时接近车身最宽处(0.1885)。
  // 胎侧外平面在 0.1867，必须被拱唇包住，否则轮胎会从侧壁穿出来。
  const lipZ = 0.1710 + 0.0175 * Math.sqrt(flare);
  // 轮井内壁：闭合时收到地板下方，避免门槛处出现凸棱
  const wellY = rc + (ay - rc) * 0.88;
  const wellZ = 0.1305 + 0.0055 * open;

  const right = [];
  // 底板：中心 → 门槛
  seg(right, [0, rc], [0.044, rc - 0.0038], [0.084, rc - 0.0008], [0.104, rc], 4, false);
  // 门槛下缘微收
  line(right, [0.104, rc], [0.120, rc + 0.0018], 1, true);
  // 轮井内壁（近乎竖直，只在内侧出现）
  line(right, [0.120, rc + 0.0018], [wellZ, wellY], 2, true);
  // 轮井顶 → 拱唇
  seg(right, [wellZ, wellY], [wellZ + (lipZ - wellZ) * 0.55, ay - (ay - wellY) * 0.20],
    [lipZ * 0.992, ay - 0.0016], [lipZ, ay], 2, true);
  // 侧面：拱唇 → 腰线。
  // 原来是单段缓坡，z 从 0.171 变到 0.194 却跨越整个车高 —— 法线几乎恒定水平，
  // 正侧视图里把环境贴图的一整块亮部原样镜射回来，读成一片死白。
  // 现在拆成「先收再鼓再收」：0.55H 处是整车最瘦的腿腰线，0.72H 处鼓回最宽，
  // 法线沿车高连续摆动，反射自然长出明暗层次。
  const H = Math.max(0.02, wa - ay);
  seg(right, [lipZ, ay], [lipZ * 1.024, ay + H * 0.16],
    [wb * 0.976, ay + H * 0.36], [wb * 0.9950, ay + H * 0.56], 6, true);
  seg(right, [wb * 0.9950, ay + H * 0.56], [wb * 1.0048, ay + H * 0.73],
    [wb * 1.0015, ay + H * 0.89], [wb, wa], 6, true);

  const out = [];
  for (let i = right.length - 1; i >= 0; i--) out.push([-right[i][0], right[i][1]]);
  for (let i = 1; i < right.length; i++) out.push(right[i]);
  return out;
}

// ───────────── 截面：上部侧围 / 车顶 ─────────────
// 腰线以上分两段：先近乎竖直地升到「肩线」，再明显内倾（tumblehome）收进车顶。
// 原来一根贝塞尔从腰线直接糊到车顶，侧围就是一个大圆球，整车看着像 90 年代概念车。
const SH_T = 0.44;                       // 肩线在截面上的位置
// 侧窗真实是「嵌」在车身里的：窗台面往里退、车顶边梁盖住玻璃上沿。
// 之前玻璃和车漆共面，整块侧围是一张连续曲面，侧窗和车顶在反光里连成一片，
// 侧视图读出来就是「戴了顶帽子」。现在玻璃整体内凹 20mm，台阶负责把
// 窗下沿、车顶边梁这两条棱线立起来。
// 注意 t 不是弧长：t ∈ [0, SH_T] 映射到 34mm 高的肩线以下，t ∈ [SH_T, 1]
// 再走 43mm 到车顶侧沿。窗下沿要落在腰线上方约 30mm，所以 DLO_T0 得取到 0.16，
// 早先给 0.078 只对应 6mm，窗台面被压成一条线。
const DLO_T0 = 0.160, DLO_T1 = 0.860;    // 侧窗上下沿在截面上的位置
const DLO_INSET = 0.0042;                // 20mm @ 4780mm 车长
function dloInset(t) {
  const k = Math.min(1, Math.max(0, Math.min((t - DLO_T0) / 0.05, (DLO_T1 - t) / 0.05)));
  return DLO_INSET * (k * k * (3 - 2 * k));
}
function upperRightPt(x, t) {
  const p = prof(x);
  const yt = p.top - RAIL_DROP;
  const wa = p.waist, wb = p.wb, wt = p.wt;
  const h = Math.max(0.010, yt - wa);
  const shY = wa + h * SH_T;
  let out;
  if (t <= SH_T) {
    const u = t / SH_T;
    // 腰线 → 肩线这一段必须真的内倾（tumblehome），不能是竖直平板。
    // 原来只外凸 0.40%，法线几乎水平，整段车侧把天空亮部原样反射回来，
    // 正侧视图里就是那顶「银色泡泡帽」。现在收到 8.5%，法线以近 9° 连续上仰，
    // 反射沿车高自然过渡，同时腰线保持整车最宽，棱线不丢。
    const k = u * u * (3 - 2 * u);
    out = [wb * (1 - 0.085 * k), wa + h * SH_T * u];
  } else {
    const u = (t - SH_T) / (1 - SH_T);
    out = bez3([wb * 0.915, shY], [wb * 0.900, shY + h * 0.20],
      [wt + (wb - wt) * 0.34, yt - h * 0.10], [wt, yt], u);
  }
  out[0] -= dloInset(t);
  return out;
}
// 侧围只造「右侧」半片：腰线 → 车顶侧沿。左侧由镜像几何生成。
// 之前试图用一条环把左右两侧连起来，结果环里多出一条横穿座舱的翻边，
// 与真实曲面共面打架，引擎盖/车顶就长出一片鳞状斑点。
// 截面采样密度：材质分界的锯齿宽度由放样行距决定（xs 方向），
// 截面方向需要保证曲面本身够圆，同时侧窗内凹的两个台阶（DLO_T0/T1）
// 必须有落在台阶上的顶点，否则窗沿会被抹成一条渐变，棱线立不起来。
const UP_T = (() => {
  const s = new Set();
  for (let i = 0; i <= 48; i++) s.add(i / 48);
  [SH_T, DLO_T0, DLO_T0 + 0.05, DLO_T1 - 0.05, DLO_T1].forEach(v => s.add(v));
  return [...s].sort((a, b) => a - b);
})();
function upperPts(x) {
  const out = [];
  for (let i = 0; i < UP_T.length; i++) out.push(upperRightPt(x, UP_T[i]));
  return out;
}
function roofPt(x, s) {
  const p = prof(x);
  const yt = p.top - RAIL_DROP;
  const wt = p.wt;
  const tt = s < 0.5 ? s * 2 : (1 - s) * 2;
  const q = bez3([wt, yt], [wt * 0.60, yt + 0.0042],
    [wt * 0.26, yt + 0.0092], [0, p.top], tt);
  return [s < 0.5 ? q[0] : -q[0], q[1]];
}
// 车顶：左沿 → 中线 → 右沿，一张连续薄板。
// 按 s 均匀采样，这样放样时 j/(S-1) 就等于 s，可以直接拿来打玻璃材质。
const RF_N = 52;
function roofPts(x) {
  const out = [];
  for (let i = 0; i <= RF_N; i++) out.push(roofPt(x, i / RF_N));
  return out;
}
// 在车顶带上按 z 反查点（z 取正值侧），给引擎盖/尾箱盖的分缝线找落点用
function roofAt(x, z) {
  let lo = 0, hi = 0.5;
  for (let i = 0; i < 26; i++) {
    const m = (lo + hi) * 0.5;
    if (roofPt(x, m)[0] > z) lo = m; else hi = m;
  }
  return roofPt(x, (lo + hi) * 0.5);
}

// ───────────── 玻璃区域（在曲面参数空间里划分，不再贴浮片） ─────────────
// A/C 柱在侧围上是一条斜线。原来 A 柱从 x=-0.177 斜到 -0.073（侧视投影 500mm），
// 相当于把风挡的 18° 直接搬到了柱子上；实际 A 柱同时向内收，侧视投影要陡得多。
// 现在：A 柱底 -0.115 → 顶 -0.045（投影 335mm / 倾角 47°），C 柱底 0.255 → 顶 0.195。
const A_PILLAR = t => -0.115 + 0.070 * t;
const C_PILLAR = t => 0.255 - 0.060 * t;
const B_X0 = 0.0580, B_X1 = 0.0860;           // B 柱（亮黑）

function upperMat(x, t) {
  if (t < DLO_T0 || t > DLO_T1) return 0;
  if (x < A_PILLAR(t) || x > C_PILLAR(t)) return 0;
  if (x > B_X0 && x < B_X1) return 2;
  return 1;
}
// 前风挡 / 天窗 / 后风挡连成一片，两侧留出车顶封边。
// 关键是这个封边不能是恒定的：风挡是梯形，cowl 处最宽、靠近车顶最窄。
// 原来 TOP_S0=0.19 恒定，风挡就是一条等宽窄带，跟侧窗上的 A 柱接不上，
// 3/4 视角下车头向左前方糊成一顶黑罩 —— 这是「A 柱消失」的真正原因。
const TOP_GLASS = [-0.190, 0.330];            // 风挡起始（cowl） → 后风挡下沿
const GLASS_A_TOP = -0.045;                   // A 柱顶所在的 x
const GLASS_C_TOP = 0.195;                    // C 柱顶所在的 x
const GLASS_EDGE = 0.175;                     // 车顶段玻璃的横向边距

// 风挡：cowl 处边距 0.070（玻璃几乎铺满），A 柱顶处收窄到 0.175
function windshieldS0(x) {
  const u = Math.min(1, Math.max(0, (x - TOP_GLASS[0]) / (GLASS_A_TOP - TOP_GLASS[0])));
  return 0.070 + (GLASS_EDGE - 0.070) * u;
}
// 后风挡：C 柱顶 0.175 → 尾箱 0.085
function backlightS0(x) {
  const u = Math.min(1, Math.max(0, (x - GLASS_C_TOP) / (TOP_GLASS[1] - GLASS_C_TOP)));
  return GLASS_EDGE - (GLASS_EDGE - 0.085) * u;
}
function glassEdgeS(x) {
  return x < GLASS_A_TOP ? windshieldS0(x)
    : (x > GLASS_C_TOP ? backlightS0(x) : GLASS_EDGE);
}

// ───────────── 车顶玻璃：单独一张曲面，不再按四边形二值分桶 ─────────────
// 老做法是在整张车顶带上按四边形中心判定「这一格是漆还是玻璃」，
// 于是梯形玻璃的斜边被量化成放样网格：行距 × 列距一格一个台阶，近看就是锯齿。
// 加密网格只能把锯齿变小、永远消不掉，而且三角数按平方涨。
// 现在改成参数重排：玻璃自带一张曲面，横向参数 u ∈ [0,1] 直接映射到
// s = a(x) + u·(1-2a(x))，于是边界恒定落在 u=0 / u=1 两列上，
// 不管网格多稀，梯形边都是解析上精确的一条线。
// 玻璃与车漆同面，用 polygonOffset 压在漆面上（decal 常规做法），不会 z-fighting。
const GLASS_COLS = 30;
function glassPts(x) {
  const a = glassEdgeS(x);
  const out = [];
  for (let j = 0; j <= GLASS_COLS; j++) {
    const u = j / GLASS_COLS;
    out.push(roofPt(x, a + u * (1 - 2 * a)));
  }
  return out;
}
// a(x) 是分段线性的，折点在 A 柱顶 / C 柱顶，这两行必须精确落上；
// 其余行只负责贴合车顶在 x 方向的曲率，不需要太密。
const GLASS_XS = (() => {
  const raw = [GLASS_A_TOP, GLASS_C_TOP];
  const N = 76;
  for (let i = 0; i <= N; i++) raw.push(TOP_GLASS[0] + (TOP_GLASS[1] - TOP_GLASS[0]) * i / N);
  raw.sort((a, b) => a - b);
  const out = [];
  for (const v of raw) if (!out.length || v - out[out.length - 1] > 1e-5) out.push(v);
  return out;
})();
const glassMatDecal = glassMat.clone();
glassMatDecal.polygonOffset = true;
glassMatDecal.polygonOffsetFactor = -2;
glassMatDecal.polygonOffsetUnits = -4;

// ───────────── 放样 ─────────────
// cap=false 时不封端，改由 endCapGeo() 用整圈轮廓统一封一次，
// 免得三张分带各自封盖在车头/车尾叠出三层共面片（z-fighting）。
function buildLoft(ptsFn, xs, matFn, cap = true) {
  const rowsPts = xs.map(ptsFn);
  const S = rowsPts[0].length;
  const pos = [];
  for (let i = 0; i < xs.length; i++) {
    for (const q of rowsPts[i]) pos.push(xs[i], q[1], q[0]);
  }
  const capA = pos.length / 3;
  if (cap) {
    let sy = 0; for (const q of rowsPts[0]) sy += q[1];
    pos.push(xs[0], sy / S, 0);
  }
  const capB = pos.length / 3;
  if (cap) {
    const last = rowsPts[xs.length - 1];
    let sy = 0; for (const q of last) sy += q[1];
    pos.push(xs[xs.length - 1], sy / S, 0);
  }

  const idx = [], mat = [];
  const push = (a, b, c, m) => { idx.push(a, b, c); mat.push(m); };
  for (let i = 0; i < xs.length - 1; i++) {
    const xm = (xs[i] + xs[i + 1]) * 0.5;
    for (let j = 0; j < S - 1; j++) {
      const a = i * S + j, b = i * S + j + 1;
      const c = (i + 1) * S + j, d = (i + 1) * S + j + 1;
      const m = matFn ? matFn(xm, (j + 0.5) / (S - 1)) : 0;
      push(a, c, b, m);
      push(b, c, d, m);
    }
  }
  if (cap) {
    const lr = (xs.length - 1) * S;
    for (let j = 0; j < S - 1; j++) {
      push(capA, j + 1, j, 0);
      push(capB, lr + j, lr + j + 1, 0);
    }
  }

  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  // 玻璃 / 亮黑件不再另做浮片，直接按四边形打材质桶：
  // 同一张曲面，永远不会出现贴片悬空、边缘漏缝或 z-fighting。
  const buckets = [[], [], []];
  for (let t = 0; t < mat.length; t++) {
    buckets[mat[t] || 0].push(idx[t * 3], idx[t * 3 + 1], idx[t * 3 + 2]);
  }
  geo.setIndex([...buckets[0], ...buckets[1], ...buckets[2]]);
  geo.addGroup(0, buckets[0].length, 0);
  geo.addGroup(buckets[0].length, buckets[1].length, 1);
  geo.addGroup(buckets[0].length + buckets[1].length, buckets[2].length, 2);
  geo.computeVertexNormals();
  return geo;
}
const BODY_MATS = [paintMat, glassMat, pianoMat];

// 侧窗内凹在 DLO_T0/T1 处是硬台阶，端盖轮廓必须精确落在这些行上，
// 否则端盖和放样之间会裂开一道缝。
const OUTLINE_T = UP_T.slice();

// 整圈截面轮廓：右腰 → 底 → 左腰 → 左顶 → 中线 → 右顶
function outline(x) {
  const loop = [];
  const low = lowerPts(x);
  for (let i = low.length - 1; i >= 0; i--) loop.push(low[i]);
  for (let i = OUTLINE_T.length - 1; i >= 0; i--) {
    const p = upperRightPt(x, OUTLINE_T[i]);
    loop.push([-p[0], p[1]]);
  }
  for (let i = 1; i <= 12; i++) loop.push(roofPt(x, i / 12));
  for (let i = 0; i < OUTLINE_T.length; i++) loop.push(upperRightPt(x, OUTLINE_T[i]));
  return loop;
}

// 车头/车尾的整块封板（扇形三角化）。
// 纯平面封板在任何非正前视角下都像被一刀切掉，所以按向心程度做前凸：
// 最外环仍精确落在放样末端（接缝无缝），越靠中心越往前鼓。
function endCapGeo(x, front) {
  const loop = outline(x);
  let area = 0;
  for (let i = 0; i < loop.length; i++) {
    const a = loop[i], b = loop[(i + 1) % loop.length];
    area += a[0] * b[1] - b[0] * a[1];
  }
  const want = front ? 1 : -1;          // 面积符号与需要的外法线方向
  if (Math.sign(area) !== want) loop.reverse();

  let cy = 0, cz = 0;
  for (const p of loop) { cy += p[1]; cz += p[0]; }
  cy /= loop.length; cz /= loop.length;

  const RINGS = 5, C = loop.length;
  // 前凸量决定车头/车尾读成「一刀切」还是「鼻锥」。7.5mm 在 1/64 车长的尺度上
  // 等于没有，任何斜视角都还是平板；提到 14mm 才会形成可见的圆角收拢。
  const bulge = (front ? -1 : 1) * 0.0140;
  const pos = [];
  for (let r = 0; r < RINGS; r++) {
    const sh = 1 - r / RINGS;           // 环半径比例
    const bx = x + bulge * (1 - sh * sh);
    for (const p of loop) {
      pos.push(bx, cy + (p[1] - cy) * sh, cz + (p[0] - cz) * sh);
    }
  }
  const c0 = pos.length / 3;
  pos.push(x + bulge, cy, cz);

  const idx = [];
  for (let r = 0; r < RINGS - 1; r++) {
    for (let i = 0; i < C; i++) {
      const a = r * C + i, b = r * C + (i + 1) % C;
      const c = (r + 1) * C + i, d = (r + 1) * C + (i + 1) % C;
      // 注意：这里的「行」是向心方向而不是 +x，与外层扇形 (c0, i, i+1) 保持同向
      // 需要 (a,b,c)+(b,d,c)，跟 buildLoft 的 (a,c,b)+(b,c,d) 正好相反。
      idx.push(a, b, c, b, d, c);
    }
  }
  const inner = (RINGS - 1) * C;
  for (let i = 0; i < C; i++) idx.push(c0, inner + i, inner + (i + 1) % C);

  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  geo.setIndex(idx);
  geo.computeVertexNormals();
  return geo;
}

// 沿 z 镜像一份几何（法线翻向、三角形绕序同步反转）
function mirrorZ(src) {
  const g = src.clone();
  const p = g.attributes.position;
  for (let i = 0; i < p.count; i++) p.setZ(i, -p.getZ(i));
  p.needsUpdate = true;
  const ix = g.index.array;
  for (let i = 0; i < ix.length; i += 3) {
    const t = ix[i + 1]; ix[i + 1] = ix[i + 2]; ix[i + 2] = t;
  }
  g.index.needsUpdate = true;
  g.computeVertexNormals();
  return g;
}

// 采样行：材质（玻璃/亮黑/车漆）是按四边形二值分桶的，
// 台阶宽度直接等于放样行距。注意锯齿不只出现在 A/C 柱那两条斜边界上：
// 车顶玻璃的侧沿是「s 恒定」的一条边界，但车顶半宽 wt 随 x 变化，
// 所以它在 3D 里是一条曲线，会被行距量化成一圈折线——这就是后风挡
// 边缘一直消不掉的锯齿来源。基线必须密到 25mm 量级，斜边界再加密一档。
const XS_ROWS = (() => {
  const raw = [];
  const N = 180;
  for (let i = 0; i <= N; i++) raw.push(-0.5 + i / N);
  const dense = (a, b, n) => {
    for (let i = 0; i <= n; i++) raw.push(a + (b - a) * i / n);
  };
  // 两段斜边界：风挡/后风挡的梯形侧边，以及压在它们区间里的 A 柱 / C 柱
  dense(TOP_GLASS[0], GLASS_A_TOP, 80);
  dense(GLASS_C_TOP, TOP_GLASS[1], 60);
  // 精确分界行：车头/车尾、cowl、A 柱两端、B 柱左右沿、C 柱两端、后风挡下沿
  raw.push(-0.5, TOP_GLASS[0], -0.115, GLASS_A_TOP, B_X0, B_X1,
    GLASS_C_TOP, 0.255, TOP_GLASS[1], 0.5);
  raw.sort((a, b) => a - b);
  const out = [];
  for (const v of raw) if (!out.length || v - out[out.length - 1] > 4e-4) out.push(v);
  return out;
})();

const car = new THREE.Group();
scene.add(car);

const bodyGroup = new THREE.Group();
car.add(bodyGroup);

// 三张分带各自封口会在车头/车尾叠成三层共面片，统一改成整圈封板
const lowerGeo = buildLoft(lowerPts, XS_ROWS, null, false);
const upperGeo = buildLoft(upperPts, XS_ROWS, upperMat, false);
const roofGeo = buildLoft(roofPts, XS_ROWS, null, false);
const glassGeo = buildLoft(glassPts, GLASS_XS, null, false);
bodyGroup.add(new THREE.Mesh(lowerGeo, BODY_MATS));
bodyGroup.add(new THREE.Mesh(upperGeo, BODY_MATS));
bodyGroup.add(new THREE.Mesh(roofGeo, BODY_MATS));
bodyGroup.add(new THREE.Mesh(mirrorZ(upperGeo), BODY_MATS));
bodyGroup.add(new THREE.Mesh(glassGeo, glassMatDecal));
bodyGroup.add(new THREE.Mesh(endCapGeo(-0.5, true), paintMat));
bodyGroup.add(new THREE.Mesh(endCapGeo(0.5, false), paintMat));

// ───────────── 轮拱内衬 ─────────────
// 内衬只能待在「轮井」里（wellZ → 拱唇），半径也不能超过轮胎，
// 否则会从拱唇外侧露出一圈黑盘，整车看着像四个外挂圆板。
function archLiner(x, side) {
  const g = new THREE.Group();
  const m = trimMat.clone();
  m.side = THREE.DoubleSide;
  const r = TIRE_R + 0.0085;
  const lin = new THREE.Mesh(
    new THREE.CylinderGeometry(r, r, 0.056, 26, 1, true,
      Math.PI * 0.02, Math.PI * 0.96), m);
  lin.rotation.x = Math.PI / 2;
  lin.position.set(x, TIRE_R, side * 0.1595);
  g.add(lin);
  return g;
}
car.add(archLiner(AXLE_F, 1), archLiner(AXLE_F, -1), archLiner(AXLE_R, 1), archLiner(AXLE_R, -1));

// ───────────── 车轮 ─────────────
// 胎面用一圈完整剖面（胎肩 → 胎侧 → 胎圈），轮辋整体缩在胎圈以内，
// 这样轮胎只有一圈橡胶是「最外」的，不会出现亮轮辋从胎侧钻出来的玩具感。
function wheel(x, side) {
  const g = new THREE.Group();
  g.position.set(x, TIRE_R, side * WHEEL_Z);
  const R = TIRE_R, hw = TIRE_HW;

  const bead = 0.0520;
  const prof = [
    [bead, -hw], [0.0585, -hw * 0.82], [0.0650, -hw * 0.58],
    [0.0698, -hw * 0.30], [R, -hw * 0.07],
    [R, hw * 0.07], [0.0698, hw * 0.30], [0.0650, hw * 0.58],
    [0.0585, hw * 0.82], [bead, hw]
  ].map(([r, h]) => new THREE.Vector2(r, h));
  const tireGeo = new THREE.LatheGeometry(prof, 56);
  tireGeo.rotateX(Math.PI / 2);
  g.add(new THREE.Mesh(tireGeo, tireMat));

  const zf = side * hw * 0.60;                 // 轮辋正面
  const zb = side * hw * 0.22;                 // 轮辋背面（往里）

  // 轮辋筒：挡住透空
  const barrel = new THREE.Mesh(
    new THREE.CylinderGeometry(0.0502, 0.0502, hw * 0.92, 48, 1, true),
    new THREE.MeshStandardMaterial({ color: 0x24272c, metalness: 0.9, roughness: 0.62,
      side: THREE.DoubleSide }));
  barrel.rotation.x = Math.PI / 2;
  barrel.position.z = side * hw * 0.20;
  g.add(barrel);

  // 机加工亮唇
  const lipRing = new THREE.Mesh(new THREE.TorusGeometry(0.0498, 0.0019, 8, 56), chromeMat);
  lipRing.position.z = zf;
  g.add(lipRing);

  // 5 组双辐：从轮毂一直伸到亮唇，细而长
  const spokeMat = new THREE.MeshStandardMaterial({
    color: 0x6f767f, metalness: 0.95, roughness: 0.30 });
  const R0 = 0.0135, R1 = 0.0492;
  for (let i = 0; i < 5; i++) {
    const base = i * Math.PI * 2 / 5;
    for (const d of [-0.118, 0.118]) {
      const a = base + d;
      const s = new THREE.Mesh(
        new THREE.BoxGeometry(R1 - R0, 0.0062, 0.0082), spokeMat);
      s.position.set(Math.cos(a) * (R0 + R1) * 0.5, Math.sin(a) * (R0 + R1) * 0.5,
        zf - side * 0.0012);
      s.rotation.z = a;
      g.add(s);
    }
  }

  // 轮毂盖
  const hub = new THREE.Mesh(new THREE.CylinderGeometry(0.0118, 0.0128, 0.010, 28), chromeMat);
  hub.rotation.x = Math.PI / 2;
  hub.position.z = zf + side * 0.0016;
  g.add(hub);
  const capMark = new THREE.Mesh(new THREE.CylinderGeometry(0.0052, 0.0052, 0.0022, 20), trimMat);
  capMark.rotation.x = Math.PI / 2;
  capMark.position.z = zf + side * 0.0070;
  g.add(capMark);

  // 刹车盘 + 卡钳
  const disc = new THREE.Mesh(new THREE.CylinderGeometry(0.0428, 0.0428, 0.0048, 44), discMat);
  disc.rotation.x = Math.PI / 2;
  disc.position.z = zb - side * 0.0050;
  g.add(disc);
  const caliper = new THREE.Mesh(new THREE.BoxGeometry(0.0145, 0.0285, 0.0105),
    new THREE.MeshStandardMaterial({ color: 0x474c55, metalness: 0.62, roughness: 0.42 }));
  caliper.position.set(-0.0145, 0.0245, zb + side * 0.0015);
  caliper.rotation.z = 0.34;
  g.add(caliper);
  return g;
}
car.add(wheel(AXLE_F, 1), wheel(AXLE_F, -1), wheel(AXLE_R, 1), wheel(AXLE_R, -1));

// ───────────── 前脸 ─────────────
// 车头封板是平的，所有前脸零件都做成薄贴片（厚度 8~12mm）贴在 x≈-0.50 上，
// 不要再用大块 Box 凸出来，否则整车会读成「梯形拼贴」。
const HEAD_Y = 0.1488;
function headlamp(side) {
  const g = new THREE.Group();
  // 灯腔黑底：横向拉长、纵向压薄，读成一条「刀锋」而不是两个白方块
  const base = new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.0265, 0.1050), pianoMat);
  base.position.set(-0.5025, HEAD_Y, side * 0.0965);
  base.rotation.z = -0.075;
  base.rotation.y = side * 0.048;
  g.add(base);

  // 灯组（细长）
  const lens = new THREE.Mesh(new THREE.BoxGeometry(0.010, 0.0150, 0.0980), headMat);
  lens.position.set(-0.5058, HEAD_Y - 0.0008, side * 0.0970);
  lens.rotation.z = -0.075;
  lens.rotation.y = side * 0.048;
  g.add(lens);

  // 日行灯眉：贴着灯组上沿
  const brow = new THREE.Mesh(new THREE.BoxGeometry(0.006, 0.0034, 0.1010),
    new THREE.MeshStandardMaterial({
      color: 0xf4f9ff, emissive: 0xd4e8ff, emissiveIntensity: 2.8,
      metalness: 0.1, roughness: 0.25
    }));
  brow.position.set(-0.5068, HEAD_Y + 0.0102, side * 0.0970);
  brow.rotation.z = -0.075;
  brow.rotation.y = side * 0.048;
  g.add(brow);
  return g;
}
car.add(headlamp(1), headlamp(-1));

// 上格栅：一条很细的封闭式饰条
const nose = new THREE.Mesh(new THREE.BoxGeometry(0.010, 0.0125, 0.132), pianoMat);
nose.position.set(-0.5068, 0.1315, 0);
car.add(nose);
const noseChrome = new THREE.Mesh(new THREE.BoxGeometry(0.005, 0.0022, 0.134), chromeMat);
noseChrome.position.set(-0.5105, 0.1385, 0);
car.add(noseChrome);

// 下进气
const intake = new THREE.Mesh(new THREE.BoxGeometry(0.010, 0.0250, 0.188), trimMat);
intake.position.set(-0.5062, 0.0755, 0);
car.add(intake);
const intakeMesh = new THREE.Mesh(new THREE.BoxGeometry(0.004, 0.0195, 0.172),
  new THREE.MeshStandardMaterial({ color: 0x111318, metalness: 0.8, roughness: 0.62 }));
intakeMesh.position.set(-0.5088, 0.0755, 0);
car.add(intakeMesh);

// 两侧导流口
for (const sd of [1, -1]) {
  const duct = new THREE.Mesh(new THREE.BoxGeometry(0.010, 0.0215, 0.030), trimMat);
  duct.position.set(-0.5038, 0.0685, sd * 0.1180);
  duct.rotation.y = sd * -0.22;
  car.add(duct);
}

// ───────────── 车尾 ─────────────
// 贯穿式尾灯：薄，贴在 x≈0.50 的封板上
const tailBase = new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.0205, 0.272), pianoMat);
tailBase.position.set(0.4975, 0.1955, 0);
car.add(tailBase);
const tailBar = new THREE.Mesh(new THREE.BoxGeometry(0.008, 0.0098, 0.262), tailMat);
tailBar.position.set(0.5015, 0.1958, 0);
car.add(tailBar);

// 下方黑色扩散器
const diffuser = new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.0290, 0.222), trimMat);
diffuser.position.set(0.4985, 0.0700, 0);
car.add(diffuser);
const diffIn = new THREE.Mesh(new THREE.BoxGeometry(0.004, 0.0215, 0.204),
  new THREE.MeshStandardMaterial({ color: 0x121419, metalness: 0.8, roughness: 0.6 }));
diffIn.position.set(0.5010, 0.0700, 0);
car.add(diffIn);

// 鸭尾扰流唇：由车顶曲面本体向上抬起的一薄翼，根部内收 0.9mm 与车身贴合。
// 原来是悬在尾箱盖上方 2.7mm 的扁条，从任何角度看都是一块飞着的塑料板。
function lipPts(x) {
  const N = 18, S0 = 0.145, S1 = 0.855;
  const k = Math.max(0, Math.min(1, (x - 0.4460) / 0.0540));
  const h = 0.0148 * Math.pow(k, 1.55);
  const out = [];
  for (let i = 0; i <= N; i++) {
    const q = roofPt(x, S0 + (S1 - S0) * (i / N));
    out.push([q[0], q[1] - 0.0009]);
  }
  for (let i = N; i >= 0; i--) {
    const q = roofPt(x, S0 + (S1 - S0) * (i / N));
    out.push([q[0], q[1] + h]);
  }
  return out;
}
const LIP_XS = (() => {
  const a = [];
  for (let i = 0; i <= 22; i++) a.push(0.4460 + 0.0540 * (i / 22));
  return a;
})();
const lipMat = paintMat.clone();
lipMat.side = THREE.DoubleSide;
car.add(new THREE.Mesh(buildLoft(lipPts, LIP_XS, null, false), lipMat));

// ───────────── 钣金分缝线 ─────────────
// 贴着车顶带表面走的一根极细管：给引擎盖、尾箱盖勾出真正的缝。
// 整片侧围/引擎盖没有任何分缝，是「塑料壳廉价感」最大的来源之一。
const seamMat = new THREE.MeshBasicMaterial({
  color: 0x080a0e, transparent: true, opacity: 0.62, depthWrite: false });
function seam(pts, r) {
  return new THREE.Mesh(
    new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts),
      Math.max(24, pts.length * 4), r || 0.0012, 5, false), seamMat);
}
// 引擎盖双棱线
for (const sd of [1, -1]) {
  const pts = [];
  for (let i = 0; i <= 16; i++) {
    const x = -0.4660 + 0.2500 * (i / 16);          // -0.466 → -0.216
    const z = sd * 0.1215;
    const q = roofAt(x, 0.1215);
    pts.push(new THREE.Vector3(x, q[1] + 0.0012, z));
  }
  car.add(seam(pts));
}
// 尾箱盖前缘（后风挡下沿的钣金缝）
{
  const pts = [];
  for (let i = 0; i <= 22; i++) {
    const z = -0.1080 + 0.2160 * (i / 22);
    const q = roofAt(0.3680, Math.abs(z));
    pts.push(new THREE.Vector3(0.3680, q[1] + 0.0012, z));
  }
  car.add(seam(pts));
}

// 反光片
for (const sd of [1, -1]) {
  const refl = new THREE.Mesh(new THREE.BoxGeometry(0.005, 0.0075, 0.026),
    new THREE.MeshStandardMaterial({ color: 0x5a0d12, emissive: 0x3a0508, emissiveIntensity: 0.7 }));
  refl.position.set(0.5015, 0.1015, sd * 0.0880);
  car.add(refl);
}

// ───────────── 后视镜 ─────────────
// 挂在 A 柱根部的门板上（x≈-0.16、腰线略上方），不能悬空。
function mirror(side) {
  const g = new THREE.Group();
  // 支臂：从 A 柱根部斜伸出来的短柱（原来是根方块，一眼假）
  const arm = new THREE.Mesh(
    new THREE.CylinderGeometry(0.0050, 0.0064, 0.0180, 12), trimMat);
  arm.position.set(-0.1600, 0.2136, side * 0.1964);
  arm.rotation.x = Math.PI / 2;
  arm.rotation.z = side * 0.20;
  g.add(arm);

  // 镜壳：156×208×248mm。原来的 18×16×27mm 只有真车七成左右，
  // 3/4 视角下读成一颗挂在 A 柱外侧的小药丸，完全没有后视镜的分量。
  const cap = new THREE.Mesh(new THREE.SphereGeometry(1, 24, 16), paintMat);
  cap.scale.set(0.0078, 0.0104, 0.0124);
  cap.position.set(-0.1520, 0.2130, side * 0.2062);
  cap.rotation.z = 0.04;
  g.add(cap);

  // 镜片：朝车尾的椭圆面
  const gl = new THREE.Mesh(new THREE.CircleGeometry(1, 26), glassMat);
  gl.scale.set(0.0104, 0.0084, 1);
  gl.position.set(-0.1442, 0.2130, side * 0.2062);
  gl.rotation.y = Math.PI / 2;
  gl.rotation.z = 0.04;
  g.add(gl);

  g.add(arm, cap, gl);
  return g;
}
car.add(mirror(1), mirror(-1));

// ───────────── 门把手 / 门缝 / 侧裙 ─────────────
function handle(x, side) {
  const h = new THREE.Mesh(new THREE.BoxGeometry(0.031, 0.0065, 0.0065), chromeMat);
  h.position.set(x, 0.1985, side * pchip(K_WB, x) * 0.998);
  return h;
}
car.add(handle(-0.0830, 1), handle(-0.0830, -1), handle(0.0930, 1), handle(0.0930, -1));

for (const side of [1, -1]) {
  for (const sx of [-0.0335, 0.1760]) {
    const s = new THREE.Mesh(new THREE.BoxGeometry(0.0018, 0.132, 0.0030),
      new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.34 }));
    s.position.set(sx, 0.124, side * (pchip(K_WB, sx) * 0.9995));
    car.add(s);
  }
}

// 腰线镀铬：沿实际腰线放样
function beltTrim(side) {
  const pos = [], idxArr = [];
  const n = 30;
  for (let i = 0; i <= n; i++) {
    const x = -0.170 + (0.470 + 0.170) * i / n;
    const wb = pchip(K_WB, x) * 0.999;
    const wa = pchip(K_WAIST, x);
    pos.push(x, wa + 0.0015, side * wb);
    pos.push(x, wa - 0.0012, side * wb);
  }
  for (let i = 0; i < n; i++) {
    const a = i * 2, b = a + 1, c = a + 2, d = a + 3;
    if (side > 0) idxArr.push(a, c, b, b, c, d);
    else idxArr.push(a, b, c, b, d, c);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setIndex(idxArr);
  g.computeVertexNormals();
  // 窗线做暗色窗框，镀铬在前脸/门把手上已经够了，再多会把车漆压死
  const m = new THREE.Mesh(g, new THREE.MeshStandardMaterial({
    color: 0x15181d, metalness: 0.85, roughness: 0.34, side: THREE.DoubleSide }));
  return m;
}
car.add(beltTrim(1), beltTrim(-1));

// ───────────── 地面阴影 ─────────────
const sc = document.createElement('canvas');
sc.width = 512; sc.height = 256;
const sx2 = sc.getContext('2d');
const gr = sx2.createRadialGradient(256, 128, 8, 256, 128, 250);
gr.addColorStop(0, 'rgba(0,0,0,0.52)');
gr.addColorStop(0.38, 'rgba(0,0,0,0.28)');
gr.addColorStop(0.72, 'rgba(0,0,0,0.09)');
gr.addColorStop(1, 'rgba(0,0,0,0)');
sx2.fillStyle = gr;
sx2.fillRect(0, 0, 512, 256);
const shadow = new THREE.Mesh(new THREE.PlaneGeometry(1.24, 0.60),
  new THREE.MeshBasicMaterial({
    map: new THREE.CanvasTexture(sc), transparent: true,
    depthWrite: false, opacity: 0.95
  }));
shadow.rotation.x = -Math.PI / 2;
shadow.position.set(0, 0.0006, 0);
scene.add(shadow);

// ───────────── 灯光 ─────────────
const key = new THREE.DirectionalLight(0xffffff, 2.1);
key.position.set(-0.9, 2.2, 1.3);
scene.add(key);
const rim = new THREE.DirectionalLight(0xa6d4ff, 1.45);
rim.position.set(1.7, 1.05, -1.1);
scene.add(rim);
const fill = new THREE.DirectionalLight(0xfff3e6, 0.5);
fill.position.set(0.5, 0.9, -1.7);
scene.add(fill);

// ───────────── 交互与循环 ─────────────
function resize() {
  const w = canvas.clientWidth || 900, h = canvas.clientHeight || 620;
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
}
window.addEventListener('resize', resize);

let dragging = false, lastX = 0, yaw = 0, targetYaw = 0;
canvas.addEventListener('pointerdown', e => { dragging = true; lastX = e.clientX; });
window.addEventListener('pointerup', () => { dragging = false; });
window.addEventListener('pointermove', e => {
  if (!dragging) return;
  targetYaw += (e.clientX - lastX) * 0.006;
  targetYaw = Math.max(-0.95, Math.min(0.95, targetYaw));
  lastX = e.clientX;
});

const clock = new THREE.Clock();
let auto = 0;
function tick() {
  const dt = Math.min(0.05, clock.getDelta());
  if (!dragging) {
    auto += dt;
    targetYaw = Math.sin(auto * 0.30) * 0.075;
  }
  yaw += (targetYaw - yaw) * Math.min(1, dt * 6);
  car.rotation.y = yaw;
  if (!window.__lockCam) {
    camera.position.set(CAM.x, CAM.y, CAM.z);
    camera.lookAt(CAM.tx, CAM.ty, CAM.tz);
  }
  renderer.render(scene, camera);
  window.__ready = true;
  requestAnimationFrame(tick);
}

resize();
tick();

window.setPaint = k => {
  if (!PAINTS[k]) return;
  paintKey = k;
  paintMat.color.setHex(PAINTS[k].color);
  paintMat.metalness = PAINTS[k].metalness;
  paintMat.roughness = PAINTS[k].roughness;
};
window.setLocked = locked => {
  headMat.emissiveIntensity = locked ? 0.12 : 1.8;
  tailMat.emissiveIntensity = locked ? 0.55 : 1.8;
  headMat.needsUpdate = true;
  tailMat.needsUpdate = true;
};
window.__car = car;
window.__cam = camera;
window.__scene = scene;
