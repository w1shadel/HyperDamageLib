#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
out vec4 fragColor;

uniform vec2 InSize;
uniform float DecayTime;
uniform float Intensity;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
    mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(0.87758, 0.47942, -0.47942, 0.87758);
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

    vec2 toCenter = uv - vec2(0.5);
    toCenter.y *= InSize.y / InSize.x;
    float dist = length(toCenter);
    float visualIntensity = Intensity > 0.0 ? clamp(pow(Intensity, 0.25), 0.0, 1.0) : 0.0;


    vec2 noiseUV = uv * 3.5 + vec2(0.0, DecayTime * 0.15);
    float n = fbm(noiseUV);
    float distortedDist = dist + (n - 0.5) * 0.25 * visualIntensity;

    float erosionStart = mix(0.46, 0.04, visualIntensity);
    float erosionWidth = 0.28;

    // 侵食の影（黒紫）の不透明度
    float shadowFactor = smoothstep(erosionStart, erosionStart + erosionWidth, distortedDist) * visualIntensity;

    float edgeFactor = smoothstep(erosionStart - 0.02, erosionStart + 0.12, distortedDist)
    * (1.0 - smoothstep(erosionStart + 0.12, erosionStart + 0.22, distortedDist))
    * visualIntensity;

    vec2 sparkUV = uv * 20.0 + vec2(sin(DecayTime * 0.5), cos(DecayTime * 0.7)) * 0.25;
    float sparkNoise = fbm(sparkUV);
    float sparkFactor = smoothstep(0.38, 0.72, sparkNoise) * shadowFactor * 1.5;

    vec3 col = sceneColor.rgb;

    vec3 decayBaseColor = vec3(0.01, 0.0, 0.03);

    vec3 edgePurpleColor = vec3(0.68, 0.05, 1.0);

    vec3 sparkColor = vec3(0.85, 0.20, 1.0);

    col = mix(col, decayBaseColor, shadowFactor * 0.92);

    col = mix(col, edgePurpleColor, edgeFactor * 0.85);

    col = mix(col, sparkColor, clamp(sparkFactor, 0.0, 1.0) * 0.9);

    fragColor = vec4(col, sceneColor.a);
}