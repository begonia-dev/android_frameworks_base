/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include "utils/MathUtils.h"

#include <SkImage.h>
#include <SkImageFilter.h>
#include <SkPoint.h>
#include <SkRect.h>
#include <SkRuntimeEffect.h>

namespace android::uirenderer {

class StretchEffect {
public:

    StretchEffect(const SkVector& direction,
                  float maxStretchAmountX,
                  float maxStretchAmountY)
            : maxStretchAmountX(maxStretchAmountX)
            , maxStretchAmountY(maxStretchAmountY)
            , mStretchDirection(direction) { }

    StretchEffect() {}

    bool isEmpty() const {
        return MathUtils::isZero(mStretchDirection.x()) && MathUtils::isZero(mStretchDirection.y());
    }

    void setEmpty() {
        *this = StretchEffect{};
    }

    StretchEffect& operator=(const StretchEffect& other) {
        this->mStretchDirection = other.mStretchDirection;
        this->maxStretchAmountX = other.maxStretchAmountX;
        this->maxStretchAmountY = other.maxStretchAmountY;
        return *this;
    }

    bool operator==(const StretchEffect& other) const {
        return mStretchDirection == other.mStretchDirection &&
                maxStretchAmountX == other.maxStretchAmountX &&
                maxStretchAmountY == other.maxStretchAmountY;
    }

    void mergeWith(const StretchEffect& other) {
        if (other.isEmpty()) {
            return;
        }
        if (isEmpty()) {
            *this = other;
            return;
        }
        mStretchDirection += other.mStretchDirection;
        if (isEmpty()) {
            return setEmpty();
        }
        maxStretchAmountX = std::max(maxStretchAmountX, other.maxStretchAmountX);
        maxStretchAmountY = std::max(maxStretchAmountY, other.maxStretchAmountY);
    }

    sk_sp<SkShader> getShader(float width, float height,
                              const sk_sp<SkImage>& snapshotImage) const;

    float maxStretchAmountX = 0;
    float maxStretchAmountY = 0;

    const SkVector getStretchDirection() const { return mStretchDirection; }

private:
    static sk_sp<SkRuntimeEffect> getStretchEffect();
    mutable SkVector mStretchDirection{0, 0};
    mutable std::unique_ptr<SkRuntimeShaderBuilder> mBuilder;
};

} // namespace android::uirenderer
