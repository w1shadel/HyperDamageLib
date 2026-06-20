#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
out vec4 fragColor;

uniform vec2 InSize;
uniform float DecayTime;
uniform float Intensity; // 0.0 ~ 1.0 (Decay率)

// 疑似乱数生成
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// 2Dバリューノイズ
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i + vec2(0.0,0.0)), hash(i + vec2(1.0,0.0)), u.x),
    mix(hash(i + vec2(0.0,1.0)), hash(i + vec2(1.0,1.0)), u.x), u.y);
}

// FBM（フラクタルノイズ）
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 4; ++i) {
        v += a * noise(p);
        p = rot * p * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec4 sceneColor = texture(DiffuseSampler, texCoord);
    vec2 uv = texCoord;

    // 画面中央からの位置ベクトル
    vec2 toCenter = uv - vec2(0.5);
    toCenter.y *= InSize.y / InSize.x;
    float dist = length(toCenter);

    // --- 【超強力ブースト：4乗根】 ---
    // わずかなDecay（例：5%）でも強烈に視覚化するため、4乗根（0.25乗）で計算します。
    // Decay  5% (0.05) -> 視覚強度 47%
    // Decay 10% (0.10) -> 視覚強度 56%
    // Decay 30% (0.30) -> 視覚強度 74%
    float visualIntensity = Intensity > 0.0 ? clamp(pow(Intensity, 0.25), 0.0, 1.0) : 0.0;

    // 1. ノイズによる歪み（生きているようなうねり）
    vec2 noiseUV = uv * 3.5 + vec2(0.0, DecayTime * 0.15);
    float n = fbm(noiseUV);
    float distortedDist = dist + (n - 0.5) * 0.25 * visualIntensity;

    // 2. 浸食領域の大きさを決定（初期の開始位置をより内側「0.46」に寄せて視界を狭めます）
    float erosionStart = mix(0.46, 0.04, visualIntensity);
    float erosionWidth = 0.28; // グラデーションの幅（少し鋭くして存在感をアピール）

    // 侵食の影（黒紫）の不透明度
    float shadowFactor = smoothstep(erosionStart, erosionStart + erosionWidth, distortedDist) * visualIntensity;

    // --- 【境界線のネオンオーラ（発光する紫のフチ）】 ---
    // 影のグラデーションが「立ち上がる部分」だけを切り取って、細いフチを生成します。
    // これにより、画面の内側ギリギリに光る紫のラインが走り、一瞬で浸食されていることが分かります。
    float edgeFactor = smoothstep(erosionStart - 0.02, erosionStart + 0.12, distortedDist)
    * (1.0 - smoothstep(erosionStart + 0.12, erosionStart + 0.22, distortedDist))
    * visualIntensity;

    // 3. 画面端に散りばめる不気味な「紫の粒子」（密度・サイズ・輝度を強化）
    vec2 sparkUV = uv * 20.0 + vec2(sin(DecayTime * 0.5), cos(DecayTime * 0.7)) * 0.25;
    float sparkNoise = fbm(sparkUV);
    // 侵食の影が濃い部分に、かなりはっきりとした輝度で散りばめます
    float sparkFactor = smoothstep(0.38, 0.72, sparkNoise) * shadowFactor * 1.5;

    // 4. カラーの最終ブレンド
    vec3 col = sceneColor.rgb;

    // 侵食の土台となる暗紫色の深い闇
    vec3 decayBaseColor = vec3(0.01, 0.0, 0.03);

    // 境界に走る、怪しく光る「ネオンパープル」
    vec3 edgePurpleColor = vec3(0.68, 0.05, 1.0);

    // 散りばめる明滅粒子用の「発光パープル」
    vec3 sparkColor = vec3(0.85, 0.20, 1.0);

    // ① まず全体に「暗い影（浸食のベース）」をミックス
    col = mix(col, decayBaseColor, shadowFactor * 0.92);

    // ② 影の「フチ（境界線）」に、怪しく光るネオン紫を重ねる
    col = mix(col, edgePurpleColor, edgeFactor * 0.85);

    // ③ さらにその上に、チカチカと明滅する紫の塵を散りばめる
    col = mix(col, sparkColor, clamp(sparkFactor, 0.0, 1.0) * 0.9);

    fragColor = vec4(col, sceneColor.a);
}