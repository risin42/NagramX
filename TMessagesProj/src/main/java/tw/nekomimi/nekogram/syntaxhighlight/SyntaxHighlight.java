/*
 * Copyright (C) 2019-2022 qwq233 <qwq233@qwq2333.top>
 * https://github.com/qwq233/Nullgram
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this software.
 *  If not, see
 * <https://www.gnu.org/licenses/>
 */

package tw.nekomimi.nekogram.syntaxhighlight;

import android.graphics.Color;
import android.text.Spannable;
import android.text.Spanned;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TextStyleSpan;

public class SyntaxHighlight {

    public static void highlight(TextStyleSpan.TextStyleRun run, Spannable spannable) {
        if (run.urlEntity instanceof TLRPC.TL_messageEntityHashtag) {
            var length = run.end - run.start;
            if (length == 7 || length == 9) {
                try {
                    int color = Color.parseColor(spannable.subSequence(run.start, run.end).toString());
                    spannable.setSpan(new ColorHighlightSpan(color, run), run.end - 1, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (IllegalArgumentException ignore) {
                }
            }
        }
    }
}
