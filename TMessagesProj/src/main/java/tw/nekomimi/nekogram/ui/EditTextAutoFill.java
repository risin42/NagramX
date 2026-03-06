package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.ui.Components.EditTextBoldCursor;

public class EditTextAutoFill extends EditTextBoldCursor {
    public EditTextAutoFill(Context context) {
        super(context);
        setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
    }

    @Override
    public int getAutofillType() {
        return AUTOFILL_TYPE_TEXT;
    }
}
