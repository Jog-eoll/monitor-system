package com.gateway.device.protocol.base.jetfileii.standard.file;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文件扩展名分组配置 —— image / video / nmg / pmg / qst 五类。
 *
 * <p>默认值取自 {@link DefaultFileExtensions} 各常量。</p>
 */
public interface JetFileIIFileExtensions {

    static JetFileIIFileExtensions defaults() {
        return new Defaults();
    }

    List<String> getImage();

    List<String> getVideo();

    List<String> getNmg();

    List<String> getPmg();

    List<String> getQst();

    @Getter
    final class Defaults implements JetFileIIFileExtensions {
        private final List<String> image;
        private final List<String> video;
        private final List<String> nmg;
        private final List<String> pmg;
        private final List<String> qst;

        Defaults() {
            this.image = Collections.unmodifiableList(new ArrayList<>(DefaultFileExtensions.IMAGE));
            this.video = Collections.unmodifiableList(new ArrayList<>(DefaultFileExtensions.VIDEO));
            this.nmg = Collections.unmodifiableList(new ArrayList<>(DefaultFileExtensions.NMG));
            this.pmg = Collections.unmodifiableList(new ArrayList<>(DefaultFileExtensions.PMG));
            this.qst = Collections.unmodifiableList(new ArrayList<>(DefaultFileExtensions.QST));
        }
    }
}
