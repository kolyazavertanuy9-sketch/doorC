#version 330

uniform sampler2D InSampler;
uniform sampler2D HistorySampler;

layout(std140) uniform MotionConfig {
    float MotionStrength;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 currentFrame = texture(InSampler, texCoord);
    vec4 previousFrame = texture(HistorySampler, texCoord);
    float historyValid = step(0.5, previousFrame.a);
    vec3 color = mix(currentFrame.rgb, previousFrame.rgb, MotionStrength * historyValid);
    fragColor = vec4(color, 1.0);
}
