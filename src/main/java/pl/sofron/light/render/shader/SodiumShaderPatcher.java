package pl.sofron.light.render.shader;

/** Deterministic patch for Sodium 0.9.2 block-layer shaders.
 * This is pure source transformation; pipeline registration is not yet wired.
 */
public final class SodiumShaderPatcher {
  private SodiumShaderPatcher() {}
  private static String once(String shader, String needle, String replacement) {
    int at=shader.indexOf(needle);
    if(at<0 || shader.indexOf(needle,at+needle.length())>=0)
      throw new IllegalArgumentException("Unexpected Sodium 0.9.2 shader layout: "+needle);
    return shader.substring(0,at)+replacement+shader.substring(at+needle.length());
  }
  public static String vertex(String shader) {
    shader=once(shader, "uniform isamplerBuffer u_SectionTimeInfo;", "#ifndef OIT_ALPHA_ONLY\nlayout(location = 4) out vec3 spl_SurfaceCameraPos;\n#endif\nuniform isamplerBuffer u_SectionTimeInfo;");
    return once(shader,"vec3 position = _vert_position + translation;","vec3 position = _vert_position + translation;\n#ifndef OIT_ALPHA_ONLY\n    spl_SurfaceCameraPos = position;\n#endif");
  }
  public static String fragment(String shader) {
    shader=once(shader,"uniform sampler2D u_BlockTex;", "#ifndef OIT_ALPHA_ONLY\nlayout(location = 4) in vec3 spl_SurfaceCameraPos;\nuniform samplerBuffer spl_LightData;\nuniform int spl_LightCount;\nuniform float spl_Intensity;\nvec3 spl_evaluate(vec3 worldPos) {\n vec3 sum=vec3(0.0);\n for(int i=0;i<min(spl_LightCount,256);i++) {\n  vec4 p=texelFetch(spl_LightData,i*2);\n  vec4 c=texelFetch(spl_LightData,i*2+1);\n  float radius=max(p.w,0.001);\n  float attenuation=clamp(1.0-length(worldPos-p.xyz)/radius,0.0,1.0);\n  attenuation*=attenuation;\n  attenuation*=attenuation;\n  sum+=max(c.rgb,vec3(0.0))*max(c.a,0.0)*attenuation;\n }\n return max(sum*max(spl_Intensity,0.0),vec3(0.0));\n}\nvec3 spl_apply(vec3 base, vec3 light) {\n vec3 mapped=vec3(1.0)-exp(-light*1.35);\n float surface=max(dot(base,vec3(0.2126,0.7152,0.0722)),0.18);\n vec3 added=mapped*surface;\n return min(vec3(1.0), base+added);\n}\n#endif\nuniform sampler2D u_BlockTex;");
    return once(shader,"fragColor = calculateFinalColor(color);","color.rgb = spl_apply(color.rgb, spl_evaluate(spl_SurfaceCameraPos));\n    fragColor = calculateFinalColor(color);");
  }
}
